// Copyright (c) 2018-2019 The Gulden developers
// Authored by: Malcolm MacLeod (mmacleod@gmx.com), Willem de Jonge (willem@isnapp.nl)
// Distributed under the GULDEN software license, see the accompanying
// file COPYING

package com.gulden.unity_wallet.main_activity_fragments

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.vision.barcode.Barcode
import com.gulden.barcodereader.BarcodeCaptureActivity
import com.gulden.jniunifiedbackend.ILibraryController
import com.gulden.unity_wallet.*
import com.gulden.unity_wallet.util.invokeNowOrOnSuccessfulCompletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.anko.contentView
import org.jetbrains.anko.design.snackbar
import org.jetbrains.anko.support.v4.alert
import kotlin.coroutines.CoroutineContext


class WalletSettingsFragment : androidx.preference.PreferenceFragmentCompat(), CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.Main + SupervisorJob()

    override fun onCreatePreferences(savedInstance: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_wallet_settings, rootKey)

        // if wallet ready, setup preference fields immediately so settings don't get removed in sight of user
        UnityCore.instance.walletReady.invokeNowOrOnSuccessfulCompletion(this) {
            if (ILibraryController.IsMnemonicWallet()) {
                preferenceScreen.removePreferenceRecursively("recovery_linked_preference")
                preferenceScreen.removePreferenceRecursively("preference_unlink_wallet")
            } else {
                preferenceScreen.removePreferenceRecursively("recovery_view_preference")
                preferenceScreen.removePreferenceRecursively("preference_remove_wallet")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as WalletActivity).showSettingsTitle(getString(R.string.title_wallet_settings))

    }

    override fun onStop() {
        super.onStop()
        (activity as WalletActivity).hideSettingsTitle()
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        when (preference?.key) {
            "preference_link_wallet" -> {
                val intent = Intent(context, BarcodeCaptureActivity::class.java)
                intent.putExtra(BarcodeCaptureActivity.AutoFocus, true)
                startActivityForResult(intent, REQUEST_CODE_SCAN_FOR_LINK)
            }
            "preference_change_pass_code" -> {
                Authentication.instance.authenticate(activity!!, getString(R.string.change_passcode_auth_title), getString(R.string.change_passcode_auth_desc)) { oldPassword ->
                    Authentication.instance.chooseAccessCode(
                            activity!!,
                            getString(R.string.change_passcode_auth_title),
                            action = { newPassword ->
                                if (!ILibraryController.ChangePassword(oldPassword.joinToString(""), newPassword.joinToString(""))) {
                                    Toast.makeText(context, "Failed to change password", Toast.LENGTH_LONG).show()
                                }
                            },
                            cancelled = {}
                    )
                }
            }
            "preference_rescan_wallet" -> {
                alert(getString(R.string.rescan_confirm_msg), getString(R.string.rescan_confirm_title)) {

                    // on confirmation compose recipient and execute payment
                    positiveButton(getString(R.string.rescan_confirm_btn)) {
                        ILibraryController.DoRescan()
                        activity?.contentView?.snackbar(getString(R.string.rescan_started))
                    }
                    negativeButton(getString(R.string.cancel_btn)) {}
                }.show()
            }
            "preference_remove_wallet", "preference_unlink_wallet" -> {
                val msg = "%s%s".format(
                        if (ILibraryController.IsMnemonicWallet())
                            getString(R.string.remove_wallet_auth_desc_recovery_warn)
                        else "",
                        getString(R.string.remove_wallet_auth_desc))
                Authentication.instance.authenticate(activity!!, getString(R.string.remove_wallet_auth_title), msg) {
                    ILibraryController.EraseWalletSeedsAndAccounts()
                    val intent = Intent(activity, WelcomeActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                }
            }
        }
        return super.onPreferenceTreeClick(preference)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_SCAN_FOR_LINK) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    val barcode = data.getParcelableExtra<Barcode>(BarcodeCaptureActivity.BarcodeObject)

                    if (!ILibraryController.IsValidLinkURI(barcode?.displayValue)) {
                        AlertDialog.Builder(context!!)
                                .setTitle(getString(com.gulden.unity_wallet.R.string.no_guldensync_warning_title))
                                .setMessage(getString(com.gulden.unity_wallet.R.string.no_guldensync_warning))
                                .setPositiveButton(getString(com.gulden.unity_wallet.R.string.button_ok)) { dialogInterface, i ->
                                    dialogInterface.dismiss()
                                }
                                .setCancelable(true)
                                .create()
                                .show()
                        return
                    }

                    // dialog helper used below
                    fun performDialog(titleResId: Int, msgResId: Int, withCancel: Boolean, action: (dialogInterface: DialogInterface) -> Unit) {
                        val builder = AlertDialog.Builder(context!!)
                                .setTitle(getString(titleResId))
                                .setMessage(getString(msgResId))
                                .setPositiveButton(getString(com.gulden.unity_wallet.R.string.button_ok)) { dialogInterface, i ->
                                    action(dialogInterface)
                                }
                                .setCancelable(true)
                        if (withCancel)
                            builder.setNegativeButton(getString(com.gulden.unity_wallet.R.string.button_cancel)) { dialogInterface, i ->
                                dialogInterface.dismiss()
                            }
                        builder.create().show()
                    }

                    if (ILibraryController.HaveUnconfirmedFunds()) {
                        performDialog(R.string.failed_guldensync_warning_title, R.string.failed_guldensync_unconfirmed_funds_message, false) {
                            it.dismiss()
                        }
                        return
                    }

                    //TODO: Refuse to link if we are in the process of a sync.

                    performDialog(
                            R.string.guldensync_info_title,
                            if (ILibraryController.GetBalance() > 0) R.string.guldensync_info_message_non_empty_wallet else R.string.guldensync_info_message_empty_wallet, true
                    ) {
                        it.dismiss()
                        (activity as WalletActivity).performLink(barcode!!.displayValue!!)
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        private const val REQUEST_CODE_SCAN_FOR_LINK = 0
    }
}
