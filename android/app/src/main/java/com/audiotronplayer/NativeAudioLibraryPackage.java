package com.audiotronplayer;

import com.facebook.react.BaseReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.module.model.ReactModuleInfo;
import com.facebook.react.module.model.ReactModuleInfoProvider;

import java.util.HashMap;
import java.util.Map;

public class NativeAudioLibraryPackage extends BaseReactPackage {

    @Override
    public NativeModule getModule(
            String name,
            ReactApplicationContext reactContext) {

        if (NativeAudioLibraryModule.NAME.equals(name)) {
            return new NativeAudioLibraryModule(reactContext);
        }

        if (NativeAudioPlayerModule.NAME.equals(name)) {
            return new NativeAudioPlayerModule(reactContext);
        }

        return null;
    }

    @Override
    public ReactModuleInfoProvider getReactModuleInfoProvider() {
        return () -> {
            Map<String, ReactModuleInfo> modules = new HashMap<>();

            modules.put(
                    NativeAudioLibraryModule.NAME,
                    new ReactModuleInfo(
                            NativeAudioLibraryModule.NAME,
                            NativeAudioLibraryModule.class.getName(),
                            false, // canOverrideExistingModule
                            false, // needsEagerInit
                            false, // isCxxModule
                            true   // isTurboModule
                    )
            );

            modules.put(
                    NativeAudioPlayerModule.NAME,
                    new ReactModuleInfo(
                            NativeAudioPlayerModule.NAME,
                            NativeAudioPlayerModule.class.getName(),
                            false, // canOverrideExistingModule
                            false, // needsEagerInit
                            false, // isCxxModule
                            true   // isTurboModule
                    )
            );

            return modules;
        };
    }
}