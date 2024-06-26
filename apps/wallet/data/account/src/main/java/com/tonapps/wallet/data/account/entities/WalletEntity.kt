package com.tonapps.wallet.data.account.entities

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import com.tonapps.wallet.data.account.Wallet
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer

data class WalletEntity(
    val id: String,
    val publicKey: PublicKeyEd25519,
    val type: Wallet.Type,
    val version: WalletVersion = WalletVersion.V4R2,
    val label: Wallet.Label
) {

    companion object {
        const val WORKCHAIN = 0
    }

    val contract: BaseWalletContract by lazy {
        val network = if (testnet) TonNetwork.TESTNET.value else TonNetwork.MAINNET.value

        BaseWalletContract.create(publicKey, version.title, network)
    }

    val testnet: Boolean
        get() = type == Wallet.Type.Testnet

    val signer: Boolean
        get() = type == Wallet.Type.Signer || type == Wallet.Type.SignerQR

    val hasPrivateKey: Boolean
        get() = type == Wallet.Type.Default || type == Wallet.Type.Testnet

    val accountId: String = contract.address.toAccountId()

    val address: String = contract.address.toWalletAddress(testnet)

    fun isMyAddress(address: String): Boolean {
        return address.toRawAddress().equals(accountId, ignoreCase = true)
    }

    fun createBody(
        seqno: Int,
        validUntil: Long,
        gifts: List<WalletTransfer>
    ): Cell {
        return contract.createTransferUnsignedBody(
            validUntil = validUntil,
            seqno = seqno,
            gifts = gifts.toTypedArray()
        )
    }

    fun sign(
        privateKeyEd25519: PrivateKeyEd25519,
        seqno: Int,
        body: Cell
    ): Cell {
        return contract.createTransferMessageCell(
            address = contract.address,
            privateKey = privateKeyEd25519,
            seqno = seqno,
            unsignedBody = body,
        )
    }
}