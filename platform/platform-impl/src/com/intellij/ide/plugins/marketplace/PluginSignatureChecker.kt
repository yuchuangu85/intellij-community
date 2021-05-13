// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.certificates.PluginCertificateStore
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.DialogAcceptanceResultEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.SignatureVerificationResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.zip.signer.signer.CertificateUtils
import org.jetbrains.zip.signer.verifier.*
import java.io.File
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit

@ApiStatus.Internal
internal object PluginSignatureChecker {
  private val LOG = logger<PluginSignatureChecker>()

  private val cache = Caffeine
    .newBuilder()
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build<String, Optional<Boolean>>()

  private val jetbrainsCertificate: Certificate? by lazy {
    val cert = PluginSignatureChecker.javaClass.classLoader.getResourceAsStream("ca.crt")
    if (cert == null) {
      LOG.warn(IdeBundle.message("jetbrains.certificate.not.found"))
      null
    }
    else {
      CertificateFactory.getInstance("X.509").generateCertificate(cert)
    }
  }

  @JvmStatic
  fun verifyPluginByAllCertificates(descriptor: IdeaPluginDescriptor, pluginFile: File): Boolean {
    val jbCert = jetbrainsCertificate ?: return processSignatureWarning(descriptor, IdeBundle.message("jetbrains.certificate.not.found"))
    val certificates = PluginCertificateStore.getInstance().customTrustManager.certificates.orEmpty() + jbCert
    return isSignedBy(descriptor, pluginFile, *certificates.toTypedArray())
  }

  @JvmStatic
  fun verifyPluginByCustomCertificates(descriptor: IdeaPluginDescriptor, pluginFile: File): Boolean {
    val certificates = PluginCertificateStore.getInstance().customTrustManager.certificates
    if (certificates.isEmpty()) return true
    return isSignedBy(descriptor, pluginFile, *certificates.toTypedArray())
  }

  @JvmStatic
  fun verifyPluginByJetBrains(descriptor: IdeaPluginDescriptor, pluginFile: File): Boolean {
    val jbCert = jetbrainsCertificate ?: return processSignatureWarning(descriptor, IdeBundle.message("jetbrains.certificate.not.found"))
    val isRevoked = isCertificatesRevoked(jbCert)
    if (isRevoked) {
      return processRevokedCertificate(descriptor)
    }
    return isSignedBy(descriptor, pluginFile, jbCert)
  }

  private fun isCertificatesRevoked(vararg certificates: Certificate): Boolean {
    val isRevokedCached = cache.getIfPresent(this.javaClass.name)?.get()
    if (isRevokedCached != null) return isRevokedCached
    val cert509Lists = certificates.mapNotNull { it as? X509Certificate }
    val lists = getRevocationLists(cert509Lists)
    val revokedCertificates = CertificateUtils.findRevokedCertificate(cert509Lists, lists)
    val isRevoked = revokedCertificates != null
    cache.put(this.javaClass.name, Optional.of(isRevoked))
    return isRevoked
  }

  private fun getRevocationLists(certs: List<X509Certificate>): List<X509CRL> {
    val certsExceptCA = certs.subList(0, certs.size - 1)
    return certsExceptCA.mapNotNull { certificate ->
      val crlUris = CertificateUtils.getCrlUris(certificate)
      if (crlUris.isEmpty()) throw IllegalArgumentException("CRL not found for certificate")
      if (crlUris.size > 1) throw IllegalArgumentException("Multiple CRL URI found in certificate")
      val crlURI = crlUris.first()
      val certificateFactory = CertificateFactory.getInstance("X.509")
      val inputStream = HttpRequests.request(crlURI.toURL().toExternalForm())
        .throwStatusCodeException(false)
        .productNameAsUserAgent()
        .connect { it.inputStream }
      certificateFactory.generateCRL(inputStream) as? X509CRL
    }
  }

  private fun isSignedBy(descriptor: IdeaPluginDescriptor, pluginFile: File, vararg certificate: Certificate): Boolean {
    val errorMessage = verifyPluginAndGetErrorMessage(descriptor, pluginFile, *certificate)
    if (errorMessage != null) {
      return processSignatureWarning(descriptor, errorMessage)
    }
    return true
  }

  private fun verifyPluginAndGetErrorMessage(descriptor: IdeaPluginDescriptor, file: File, vararg certificates: Certificate): String? {
    return when (val verificationResult = ZipVerifier.verify(file)) {
      is InvalidSignatureResult -> {
        PluginManagerUsageCollector.signatureCheckResult(descriptor, SignatureVerificationResult.INVALID_SIGNATURE)
        verificationResult.errorMessage
      }
      is MissingSignatureResult -> {
        PluginManagerUsageCollector.signatureCheckResult(descriptor, SignatureVerificationResult.MISSING_SIGNATURE)
        IdeBundle.message("plugin.signature.not.signed")
      }
      is SuccessfulVerificationResult -> {
        val isSigned = certificates.any { certificate ->
          certificate is X509Certificate && verificationResult.isSignedBy(certificate)
        }
        if (!isSigned) {
          PluginManagerUsageCollector.signatureCheckResult(descriptor, SignatureVerificationResult.WRONG_SIGNATURE)
          IdeBundle.message("plugin.signature.not.signed.by")
        }
        else {
          PluginManagerUsageCollector.signatureCheckResult(descriptor, SignatureVerificationResult.SUCCESSFUL)
          null
        }
      }
    }
  }

  private fun processRevokedCertificate(descriptor: IdeaPluginDescriptor): Boolean {
    val message = IdeBundle.message("plugin.signature.checker.revoked.cert", descriptor.name)
    return processSignatureCheckerVerdict(descriptor, message)
  }

  private fun processSignatureWarning(descriptor: IdeaPluginDescriptor, errorMessage: String): Boolean {
    val message = IdeBundle.message("plugin.signature.checker.untrusted.message", descriptor.name, errorMessage)
    return processSignatureCheckerVerdict(descriptor, message)
  }

  private fun processSignatureCheckerVerdict(descriptor: IdeaPluginDescriptor, @Nls message: String): Boolean {
    val title = IdeBundle.message("plugin.signature.checker.title")
    val yesText = IdeBundle.message("plugin.signature.checker.yes")
    val noText = IdeBundle.message("plugin.signature.checker.no")
    var result: Int = -1
    ApplicationManager.getApplication().invokeAndWait(
      { result = Messages.showYesNoDialog(message, title, yesText, noText, Messages.getWarningIcon()) },
      ModalityState.any()
    )
    PluginManagerUsageCollector.signatureWarningShown(
      descriptor,
      if (result == Messages.YES) DialogAcceptanceResultEnum.ACCEPTED else DialogAcceptanceResultEnum.DECLINED
    )
    return result == Messages.YES
  }

}