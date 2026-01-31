package org.example.bubbleapp.call

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.CallKit.*
import platform.Foundation.NSError
import platform.Foundation.NSUUID
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class CallKitManager : NSObject(), CXProviderDelegateProtocol {

    private val provider: CXProvider
    private val callController = CXCallController()

    var onAnswerCall: ((NSUUID) -> Unit)? = null
    var onEndCall: ((NSUUID) -> Unit)? = null
    var onMuteCall: ((NSUUID, Boolean) -> Unit)? = null
    var onStartCall: ((NSUUID) -> Unit)? = null

    private var activeCallUUID: NSUUID? = null

    init {
        val config = CXProviderConfiguration().apply {
            maximumCallsPerCallGroup = 1u
            supportsVideo = false
            supportedHandleTypes = setOf(CXHandleTypeGeneric)
        }
        provider = CXProvider(config)
        provider.setDelegate(this, null)
    }

    // Показать входящий звонок
    fun reportIncomingCall(
        uuid: NSUUID,
        callerName: String,
        completion: ((NSError?) -> Unit)? = null
    ) {
        activeCallUUID = uuid
        println("CallKit: Reporting incoming call from $callerName")

        val update = CXCallUpdate().apply {
            remoteHandle = CXHandle(CXHandleTypeGeneric, callerName)
            hasVideo = false
            localizedCallerName = callerName
        }

        provider.reportNewIncomingCallWithUUID(uuid, update) { error ->
            if (error != null) {
                println("CallKit: Error reporting incoming call: ${error.localizedDescription}")
                // Don't clear activeCallUUID - call can still work without CallKit UI
            } else {
                println("CallKit: Incoming call reported successfully")
            }
            completion?.invoke(error)
        }
    }

    // Начать исходящий звонок
    fun startOutgoingCall(uuid: NSUUID, handle: String, onResult: ((Boolean) -> Unit)? = null) {
        activeCallUUID = uuid
        println("CallKit: Starting outgoing call to $handle")

        val callHandle = CXHandle(CXHandleTypeGeneric, handle)
        val startAction = CXStartCallAction(uuid, callHandle)
        startAction.setVideo(false)

        val transaction = CXTransaction(startAction)
        callController.requestTransaction(transaction) { error ->
            if (error != null) {
                println("CallKit: Error starting outgoing call: ${error.localizedDescription}")
                // Don't clear activeCallUUID - call can still work without CallKit
                onResult?.invoke(false)
            } else {
                println("CallKit: Outgoing call started successfully")
                onResult?.invoke(true)
            }
        }
    }

    // Уведомить о начале соединения
    fun reportOutgoingCallStartedConnecting(uuid: NSUUID) {
        provider.reportOutgoingCallWithUUID(uuid, startedConnectingAtDate = null)
    }

    // Уведомить о подключении
    fun reportOutgoingCallConnected(uuid: NSUUID) {
        provider.reportOutgoingCallWithUUID(uuid, connectedAtDate = null)
    }

    // Завершить звонок
    fun endCall(uuid: NSUUID? = activeCallUUID) {
        uuid ?: return

        val endAction = CXEndCallAction(uuid)
        val transaction = CXTransaction(endAction)

        callController.requestTransaction(transaction) { error ->
            if (error != null) {
                println("Error ending call: ${error.localizedDescription}")
            }
        }
    }

    // Уведомить о завершении (если завершено удалённо)
    fun reportCallEnded(uuid: NSUUID? = activeCallUUID, reason: CXCallEndedReason) {
        uuid ?: return
        provider.reportCallWithUUID(uuid, endedAtDate = null, reason = reason)
        activeCallUUID = null
    }

    fun getActiveCallUUID(): NSUUID? = activeCallUUID

    // CXProviderDelegate

    override fun providerDidReset(provider: CXProvider) {
        println("CallKit provider did reset")
        activeCallUUID = null
    }

    override fun provider(provider: CXProvider, performStartCallAction: CXStartCallAction) {
        onStartCall?.invoke(performStartCallAction.callUUID)
        performStartCallAction.fulfill()
    }

    override fun provider(provider: CXProvider, performAnswerCallAction: CXAnswerCallAction) {
        onAnswerCall?.invoke(performAnswerCallAction.callUUID)
        performAnswerCallAction.fulfill()
    }

    override fun provider(provider: CXProvider, performEndCallAction: CXEndCallAction) {
        onEndCall?.invoke(performEndCallAction.callUUID)
        activeCallUUID = null
        performEndCallAction.fulfill()
    }

    override fun provider(provider: CXProvider, performSetMutedCallAction: CXSetMutedCallAction) {
        onMuteCall?.invoke(performSetMutedCallAction.callUUID, performSetMutedCallAction.muted)
        performSetMutedCallAction.fulfill()
    }
}
