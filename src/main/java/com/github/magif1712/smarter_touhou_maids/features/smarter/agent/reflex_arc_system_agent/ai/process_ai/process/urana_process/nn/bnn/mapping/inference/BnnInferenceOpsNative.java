package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping.inference;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

class BnnInferenceOpsNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    static native void _bnnForwardLayerStoreFz(
            long a_prev_pad, long q, long P,
            long l, long r, long b,
            long a_curr, long fz, long n, long n_words,
            long stream
    );

    static native void _bnnForwardLayerNoFz(
            long a_prev_pad, long q, long P,
            long l, long r, long b,
            long a_curr, long n, long n_words,
            long stream
    );
}