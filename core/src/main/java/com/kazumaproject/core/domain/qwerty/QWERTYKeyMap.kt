package com.kazumaproject.core.domain.qwerty

interface QWERTYKeyMapHolder {
    val keysDefault: Set<QWERTYKey>
    fun getKeyInfoDefault(key: QWERTYKey): QWERTYKeyInfo
}

class QWERTYKeyMap : QWERTYKeyMapHolder {

    private val listDefault: Map<QWERTYKey, QWERTYKeyInfo> = mapOf(
        QWERTYKey.QWERTYKeyA to QWERTYKeyInfo.KeyA,
        QWERTYKey.QWERTYKeyB to QWERTYKeyInfo.KeyB,
        QWERTYKey.QWERTYKeyC to QWERTYKeyInfo.KeyC,
        QWERTYKey.QWERTYKeyD to QWERTYKeyInfo.KeyD,
        QWERTYKey.QWERTYKeyE to QWERTYKeyInfo.KeyE,
        QWERTYKey.QWERTYKeyF to QWERTYKeyInfo.KeyF,
        QWERTYKey.QWERTYKeyG to QWERTYKeyInfo.KeyG,
        QWERTYKey.QWERTYKeyH to QWERTYKeyInfo.KeyH,
        QWERTYKey.QWERTYKeyI to QWERTYKeyInfo.KeyI,
        QWERTYKey.QWERTYKeyJ to QWERTYKeyInfo.KeyJ,
        QWERTYKey.QWERTYKeyK to QWERTYKeyInfo.KeyK,
        QWERTYKey.QWERTYKeyL to QWERTYKeyInfo.KeyL,
        QWERTYKey.QWERTYKeyM to QWERTYKeyInfo.KeyM,
        QWERTYKey.QWERTYKeyN to QWERTYKeyInfo.KeyN,
        QWERTYKey.QWERTYKeyO to QWERTYKeyInfo.KeyO,
        QWERTYKey.QWERTYKeyP to QWERTYKeyInfo.KeyP,
        QWERTYKey.QWERTYKeyQ to QWERTYKeyInfo.KeyQ,
        QWERTYKey.QWERTYKeyR to QWERTYKeyInfo.KeyR,
        QWERTYKey.QWERTYKeyS to QWERTYKeyInfo.KeyS,
        QWERTYKey.QWERTYKeyT to QWERTYKeyInfo.KeyT,
        QWERTYKey.QWERTYKeyU to QWERTYKeyInfo.KeyU,
        QWERTYKey.QWERTYKeyV to QWERTYKeyInfo.KeyV,
        QWERTYKey.QWERTYKeyW to QWERTYKeyInfo.KeyW,
        QWERTYKey.QWERTYKeyX to QWERTYKeyInfo.KeyX,
        QWERTYKey.QWERTYKeyY to QWERTYKeyInfo.KeyY,
        QWERTYKey.QWERTYKeyZ to QWERTYKeyInfo.KeyZ,
        QWERTYKey.QWERTYKeyShift to QWERTYKeyInfo.KeyShift,
        QWERTYKey.QWERTYKeyDelete to QWERTYKeyInfo.KeyDelete,
        QWERTYKey.QWERTYKeySwitchMode to QWERTYKeyInfo.KeySwitchMode,
        QWERTYKey.QWERTYKeySpace to QWERTYKeyInfo.KeySpace,
        QWERTYKey.QWERTYKeyReturn to QWERTYKeyInfo.KeyReturn
    )

    override val keysDefault: Set<QWERTYKey>
        get() = listDefault.keys

    override fun getKeyInfoDefault(key: QWERTYKey): QWERTYKeyInfo {
        return listDefault.getOrDefault(key, QWERTYKeyInfo.Null)
    }

}
