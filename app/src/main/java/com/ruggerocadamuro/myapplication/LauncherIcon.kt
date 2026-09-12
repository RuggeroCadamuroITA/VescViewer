package com.ruggerocadamuro.myapplication

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * L'icona dell'app e' quella ufficiale e non si cambia piu'.
 *
 * Le varianti storiche (`LauncherAlias1/2`) restano dichiarate nel manifest
 * perche' un'app non puo' modificare a runtime il proprio elenco di
 * componenti: qui vengono semplicemente spente e la MainActivity riaccesa.
 * Serve anche a riparare chi nelle versioni precedenti aveva scelto un'altra
 * icona e si ritrovava il launcher puntato su un alias.
 */
object LauncherIcon {

    fun enforceDefault(context: Context) {
        val packageManager = context.packageManager
        val pkg = context.packageName

        fun setState(component: String, enabled: Boolean) {
            runCatching {
                packageManager.setComponentEnabledSetting(
                    ComponentName(pkg, component),
                    if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }

        setState("$pkg.MainActivity", true)
        setState("$pkg.LauncherAlias1", false)
        setState("$pkg.LauncherAlias2", false)
    }
}
