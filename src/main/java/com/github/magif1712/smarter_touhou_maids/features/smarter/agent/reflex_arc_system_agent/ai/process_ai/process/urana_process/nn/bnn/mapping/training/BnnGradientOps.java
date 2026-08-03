package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping.training;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;

public class BnnGradientOps {
    public static void backwardLayer(
            IntVector da0,
            IntVector da1,
            BoolVector fz,
            BoolVector b,
            IntVector p,
            BoolVector q,
            BoolVector l,
            BoolVector r,
            IntVector dz_workspace,
            int batch_size,
            int n_curr,
            int n_prev,
            long stream) {
        BnnGradientOpsNative._backwardLayer(
                da0.requireHandle(),
                da1.requireHandle(),
                fz.requireHandle(),
                b.requireHandle(),
                p.requireHandle(),
                q.requireHandle(),
                l.requireHandle(),
                r.requireHandle(),
                dz_workspace.requireHandle(),
                batch_size,
                n_curr,
                n_prev,
                stream
        );
    }

    public static void backwardGradientDescentLayer(
            IntVector da0,
            IntVector da1,
            BoolVector a_prev,
            BoolVector fz,
            BoolVector b,
            IntVector p,
            BoolVector q,
            BoolVector l,
            BoolVector r,
            IntVector dz_workspace,
            int n_curr,
            int n_prev,
            long stream) {
        BnnGradientOpsNative._backwardGradientDescentLayer(
                da0.requireHandle(),
                da1.requireHandle(),
                a_prev.requireHandle(),
                fz.requireHandle(),
                b.requireHandle(),
                p.requireHandle(),
                q.requireHandle(),
                l.requireHandle(),
                r.requireHandle(),
                dz_workspace.requireHandle(),
                n_curr,
                n_prev,
                stream
        );
    }
}