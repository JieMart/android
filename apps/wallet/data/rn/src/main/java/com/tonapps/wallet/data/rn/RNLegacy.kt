package com.tonapps.wallet.data.rn

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.tonapps.wallet.data.rn.data.RNDecryptedData
import com.tonapps.wallet.data.rn.data.RNTC
import com.tonapps.wallet.data.rn.data.RNVaultState
import com.tonapps.wallet.data.rn.data.RNWallet
import com.tonapps.wallet.data.rn.data.RNWallets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RNLegacy(
    context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        const val DEFAULT_KEYSTORE_ALIAS = "key_v1"
    }

    private val sql = RNSql(context)
    private val seedStorage = RNSeedStorage(context)
    private var cacheWallets: RNWallets? = null

    @Volatile
    private var requestMigration: Boolean? = null

    @Volatile
    private var walletMigrated: Boolean? = null

    init {
        scope.launch(Dispatchers.IO) {
            requestMigration = sql.getValue("x") == null
            if (requestMigration == true) {
                sql.setValue("x", "true")
            } else {
                walletMigrated = true
            }
        }
    }

    fun isRequestMainMigration(): Boolean {
        while (requestMigration == null) {
            Thread.sleep(16)
        }
        return requestMigration!!
    }

    private fun isWalletMigrated(): Boolean {
        while (walletMigrated == null) {
            Thread.sleep(16)
        }
        return walletMigrated!!
    }

    fun isRequestMigration(): Boolean {
        if (!isRequestMainMigration()) {
            return false
        }
        return isWalletMigrated()
    }

    fun setWalletMigrated() {
        walletMigrated = true
    }

    suspend fun setTonProof(id: String, token: String) {
        if (getTonProof(id) == null) {
            seedStorage.setTonProof(id, token)
        }
    }

    suspend fun getTonProof(id: String): String? {
        return seedStorage.getTonProof(id)
    }

    suspend fun exportPasscodeWithBiometry(): String {
        return seedStorage.exportPasscodeWithBiometry()
    }

    suspend fun setupBiometry(passcode: String) = withContext(Dispatchers.IO) {
        seedStorage.setupBiometry(passcode)
    }

    suspend fun removeBiometry() = withContext(Dispatchers.IO) {
        seedStorage.removeBiometry()
    }

    suspend fun clearMnemonic() {
        seedStorage.removeAll()
    }

    suspend fun hasPinCode(): Boolean {
        return seedStorage.hasPinCode()
    }

    suspend fun addMnemonics(passcode: String, walletIds: List<String>, mnemonic: List<String>) {
        val vaultState = getVaultState(passcode)

        for (walletId in walletIds) {
            vaultState.keys[walletId] = RNDecryptedData(walletId, mnemonic.joinToString(" "))
        }
        seedStorage.save(passcode, vaultState)
    }

    suspend fun changePasscode(oldPasscode: String, newPasscode: String) {
        val vaultState = getVaultState(oldPasscode)
        seedStorage.save(newPasscode, vaultState)
    }

    suspend fun getVaultState(passcode: String): RNVaultState = withContext(Dispatchers.IO) {
        try {
            seedStorage.get(passcode)
        } catch (e: Throwable) {
            RNVaultState()
        }
    }

    fun getValue(key: String): String? {
        return sql.getValue(key)
    }

    fun getJSONValue(key: String): JSONObject? {
        return sql.getJSONObject(key)
    }

    fun setJSONValue(key: String, value: JSONObject) {
        sql.setJSONObject(key, value)
    }

    fun getJSONState(key: String): JSONObject? {
        return getJSONValue(key)?.getJSONObject("state")
    }

    fun setJSONState(key: String, value: JSONObject) {
        val state = JSONObject()
        state.put("state", value)
        setJSONValue(key, state)
    }

    fun setActivity(activity: FragmentActivity) {
        seedStorage.setActivity(activity)
    }

    fun getTCApps(): RNTC {
        val tcApps = getJSONState("TCApps")?.getJSONObject("connectedApps") ?: JSONObject()
        return RNTC(tcApps)
    }

    fun setTCApps(data: RNTC) {
        val json = JSONObject()
        json.put("connectedApps", data.toJSON())
        setJSONState("TCApps", json)
    }

    fun setTokenHidden(
        walletId: String,
        tokenAddress: String,
        hidden: Boolean
    ) {
        val key = "${walletId}/tokenApproval"
        val json = getJSONValue(key)?.getJSONObject("tokens") ?: JSONObject()
        if (hidden) {
            json.put(tokenAddress, JSONObject().apply {
                put("current", "declined")
                put("updated_at", System.currentTimeMillis())
            })
        } else {
            json.remove(tokenAddress)
        }
        setJSONValue(key, JSONObject().apply {
            put("tokens", json)
        })
    }

    fun getSetup(walletId: String): Pair<Boolean, Boolean> {
        val json = getJSONValue("${walletId}/setup") ?: JSONObject()
        val setupDismissed = json.optBoolean("setupDismissed", false)
        val hasOpenedTelegramChannel = json.optBoolean("hasOpenedTelegramChannel", false)
        return Pair(setupDismissed, hasOpenedTelegramChannel)
    }

    fun setSetupDismissed(walletId: String) {
        val key = "${walletId}/setup"
        val json = getJSONValue(key) ?: JSONObject()
        json.put("setupDismissed", true)
        setJSONValue(key, json)
    }

    fun setHasOpenedTelegramChannel(walletId: String) {
        val key = "${walletId}/setup"
        val json = getJSONValue(key) ?: JSONObject()
        json.put("hasOpenedTelegramChannel", true)
        setJSONValue(key, json)
    }

    fun getNotificationsEnabled(walletId: String): Boolean {
        val key = "$walletId/notifications"
        return getJSONValue(key)?.getBoolean("isSubscribed") ?: false
    }

    fun setNotificationsEnabled(walletId: String, enabled: Boolean) {
        val key = "$walletId/notifications"
        val value = getJSONValue(key) ?: JSONObject()
        value.put("isSubscribed", enabled)
        setJSONValue(key, value)
    }

    fun getHiddenTokens(walletId: String): List<String> {
        val tokens = getJSONValue("${walletId}/tokenApproval")?.getJSONObject("tokens") ?: JSONObject()
        val list = mutableListOf<String>()
        for (key in tokens.keys()) {
            val json = tokens.getJSONObject(key)
            val current = json.optString("current") ?: continue
            val hidden = current == "declined" || current == "spam"
            if (hidden) {
                list.add(key)
            }
        }
        return list
    }

    suspend fun getWallets(): RNWallets {
        return cacheWallets?.copy() ?: loadWallets().also { cacheWallets = it.copy() }
    }

    suspend fun setWallets(wallets: RNWallets) {
        cacheWallets = wallets.copy()
        saveWallets(wallets)
    }

    suspend fun setSelectedWallet(id: String) {
        val wallets = getWallets()
        if (wallets.selectedIdentifier != id) {
            setWallets(wallets.copy(
                selectedIdentifier = id
            ))
        }
    }

    suspend fun clear() {
        cacheWallets = null
        saveWallets(getWallets().copy(
            wallets = emptyList()
        ))
    }

    suspend fun addWallet(wallet: RNWallet) {
        val state = getWallets()
        val newWallets = state.copy(
            wallets = state.wallets.toMutableList().apply {
                add(wallet)
            }
        )
        setWallets(newWallets)
    }

    suspend fun edit(id: String, name: String, emoji: String, color: Int) {
        val wallets = getWallets()
        val index = wallets.wallets.indexOfFirst { it.identifier == id }
        if (index == -1) {
            return
        }
        val wallet = wallets.wallets[index]
        val newWallet = wallet.copy(
            name = name,
            emoji = emoji,
            color = RNWallet.resolveColor(color)
        )
        setWallets(wallets.copy(
            wallets = wallets.wallets.toMutableList().apply {
                set(index, newWallet)
            }
        ))
    }

    suspend fun delete(id: String) {
        val wallets = getWallets()
        val index = wallets.wallets.indexOfFirst { it.identifier == id }
        if (index == -1) {
            return
        }
        setWallets(wallets.copy(
            wallets = wallets.wallets.toMutableList().apply {
                removeAt(index)
            }
        ))
    }

    private suspend fun loadWallets(): RNWallets = withContext(Dispatchers.IO) {
        val value = getJSONValue("walletsStore") ?: return@withContext RNWallets.empty
        try {
            RNWallets(value)
        } catch (e: Throwable) {
            RNWallets.empty
        }
    }

    private suspend fun saveWallets(wallets: RNWallets) = withContext(Dispatchers.IO) {
        val json = wallets.toJSON()
        json.put("__version", 2)
        setJSONValue("walletsStore", json)
    }

}