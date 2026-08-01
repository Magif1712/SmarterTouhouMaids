package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.Hyperparameters;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.NetworkData;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.IO;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.containers.io.IOLayerGradients;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping.inference.InferenceOps;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent.ai.process_ai.process.urana_process.nn.bnn.mapping.training.GradientOps;

public class NetworkProcessor {

    public static void forwardStoreFz(NetworkData networkData, IO io, BoolVector fz, long stream) {
        Hyperparameters hyperparameters = networkData.getHyperparameters();
        InferenceOps.bnnForwardLayerStoreFz(
                io.getA0(), // a_prev_pad
                hyperparameters.getQ(),
                hyperparameters.getP(),
                hyperparameters.getL(),
                hyperparameters.getR(),
                hyperparameters.getB(),
                io.getA1(), // a_curr
                fz,
                hyperparameters.getSizeA0(), // n
                hyperparameters.getSizeA1() / 32, // n_words
                stream // CUDA stream
        );
    }

    public static void forwardNoFz(NetworkData networkData, IO io, long stream) {
        Hyperparameters hyperparameters = networkData.getHyperparameters();
        InferenceOps.bnnForwardLayerNoFz(
                io.getA0(), // a_prev_pad
                hyperparameters.getQ(),
                hyperparameters.getP(),
                hyperparameters.getL(),
                hyperparameters.getR(),
                hyperparameters.getB(),
                io.getA1(), // a_curr
                hyperparameters.getSizeA0(), // n
                hyperparameters.getSizeA1() / 32, // n_words
                stream // CUDA stream
        );
    }

    public static void backward(NetworkData networkData, IOLayerGradients gradients, BoolVector fz, long stream) throws Exception {
        Hyperparameters hyperparameters = networkData.getHyperparameters();
        // 从容器中获取向量
        IntVector da1 = gradients.getOutputLayerGradient().getVector();
        IntVector da0 = gradients.getInputLayerGradient().getVector();
        IntVector dzWorkspace = gradients.getDzWorkspace();

        // 调用封装好的、一步到位的反向传播层
        GradientOps.backwardLayer(
                da0,
                da1,
                fz,
                hyperparameters.getB(),
                hyperparameters.getP(),
                hyperparameters.getQ(),
                hyperparameters.getL(),
                hyperparameters.getR(),
                dzWorkspace,
                1, // batch_size, 假设为 1
                hyperparameters.getSizeA1(),
                hyperparameters.getSizeA0(),
                stream
        );
    }

    public static void backwardWithGradientDescent(NetworkData networkData, IOLayerGradients gradients, BoolVector fz, BoolVector a_prev, long stream) throws Exception {
        Hyperparameters hyperparameters = networkData.getHyperparameters();
        // 从容器中获取向量
        IntVector da1 = gradients.getOutputLayerGradient().getVector();
        IntVector da0 = gradients.getInputLayerGradient().getVector();
        IntVector dzWorkspace = gradients.getDzWorkspace();

        // 调用封装好的、带梯度下降的反向传播层
        GradientOps.backwardGradientDescentLayer(
                da0,
                da1,
                a_prev,
                fz,
                hyperparameters.getB(),
                hyperparameters.getP(),
                hyperparameters.getQ(),
                hyperparameters.getL(),
                hyperparameters.getR(),
                dzWorkspace,
                hyperparameters.getSizeA1(),
                hyperparameters.getSizeA0(),
                stream
        );
    }
}