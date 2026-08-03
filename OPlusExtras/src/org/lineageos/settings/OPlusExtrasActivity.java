/*
 * Copyright (C) 2017 The OmniROM Project
 *               2022 The Evolution X Project
 *               2024 The crDroid Project
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package org.lineageos.settings;

import androidx.fragment.app.Fragment;
import android.os.Bundle;
import androidx.preference.PreferenceManager;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class OPlusExtrasActivity extends CollapsingToolbarBaseActivity {

    private OPlusExtras mOPlusExtrasFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Fragment fragment = getSupportFragmentManager().findFragmentById(
                                com.android.settingslib.collapsingtoolbar.R.id.content_frame);
        if (fragment == null) {
            mOPlusExtrasFragment = new OPlusExtras();
            getSupportFragmentManager().beginTransaction()
                .add(com.android.settingslib.collapsingtoolbar.R.id.content_frame, mOPlusExtrasFragment)
                .commit();
        } else {
            mOPlusExtrasFragment = (OPlusExtras) fragment;
        }
    }
}
