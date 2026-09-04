package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.mapping.training;

import com.github.magif1712.smarter_touhou_maids.core.native_support.NativeLibLoader;

class BnnGradientOpsNative {
    static {
        NativeLibLoader.ensureLoaded();
    }

    static native void _backwardLayer(
            long da0_handle,
            long da1_handle,
            long fz_handle,
            long b_handle,
            long p_handle,
            long q_handle,
            long l_handle,
            long r_handle,
            long dz_workspace_handle,
            int batch_size,
            int n_curr,
            int n_prev,
            long stream
    );

    static native void _backwardGradientDescentLayer(
            long da0_handle,
            long da1_handle,
            long a_prev_handle,
            long fz_handle,
            long b_handle,
            long p_handle,
            long q_handle,
            long l_handle,
            long r_handle,
            long dz_workspace_handle,
            int n_curr,
            int n_prev,
            long stream
    );
}