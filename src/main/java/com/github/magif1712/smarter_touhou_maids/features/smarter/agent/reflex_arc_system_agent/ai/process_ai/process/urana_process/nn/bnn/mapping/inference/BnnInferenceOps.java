package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping.inference;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;

public class BnnInferenceOps {
    public static void bnnForwardLayerStoreFz(
            BoolVector a_prev_pad, BoolVector q, IntVector P,
            BoolVector l, BoolVector r, BoolVector b,
            BoolVector a_curr, BoolVector fz, long n, long n_words,
            long stream) {

        BnnInferenceOpsNative._bnnForwardLayerStoreFz(
                a_prev_pad.requireHandle(), q.requireHandle(), P.requireHandle(),
                l.requireHandle(), r.requireHandle(), b.requireHandle(),
                a_curr.requireHandle(), fz.requireHandle(), n, n_words,
                stream
        );
    }

    public static void bnnForwardLayerNoFz(
            BoolVector a_prev_pad, BoolVector q, IntVector P,
            BoolVector l, BoolVector r, BoolVector b,
            BoolVector a_curr, long n, long n_words,
            long stream) {

        BnnInferenceOpsNative._bnnForwardLayerNoFz(
                a_prev_pad.requireHandle(), q.requireHandle(), P.requireHandle(),
                l.requireHandle(), r.requireHandle(), b.requireHandle(),
                a_curr.requireHandle(), n, n_words,
                stream
        );
    }
}