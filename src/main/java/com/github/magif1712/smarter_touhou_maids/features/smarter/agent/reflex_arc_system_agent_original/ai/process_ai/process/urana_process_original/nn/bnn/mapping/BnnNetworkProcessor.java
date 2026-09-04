package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.mapping;

import com.github.magif1712.smarter_touhou_maids.core.containers.vector.BoolVector;
import com.github.magif1712.smarter_touhou_maids.core.containers.vector.IntVector;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.BnnHyperparameters;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.BnnNetworkData;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.BnnIO;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.containers.io.BnnIOLayerGradients;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.mapping.inference.BnnInferenceOps;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.nn.bnn.mapping.training.BnnGradientOps;

public class BnnNetworkProcessor {

    public static void forwardStoreFz(BnnNetworkData networkData, BnnIO io, BoolVector fz, long stream) {
        BnnHyperparameters hyperparameters = networkData.getHyperparameters();
        BnnInferenceOps.bnnForwardLayerStoreFz(
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

    public static void forwardNoFz(BnnNetworkData networkData, BnnIO io, long stream) {
        BnnHyperparameters hyperparameters = networkData.getHyperparameters();
        BnnInferenceOps.bnnForwardLayerNoFz(
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

    public static void backward(BnnNetworkData networkData, BnnIOLayerGradients gradients, BoolVector fz, long stream) throws Exception {
        BnnHyperparameters hyperparameters = networkData.getHyperparameters();
        // 从容器中获取向量
        IntVector da1 = gradients.getOutputLayerGradient().getVector();
        IntVector da0 = gradients.getInputLayerGradient().getVector();
        IntVector dzWorkspace = gradients.getDzWorkspace();

        // 调用封装好的、一步到位的反向传播层
        BnnGradientOps.backwardLayer(
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

    public static void backwardWithGradientDescent(BnnNetworkData networkData, BnnIOLayerGradients gradients, BoolVector fz, BoolVector a_prev, long stream) throws Exception {
        BnnHyperparameters hyperparameters = networkData.getHyperparameters();
        // 从容器中获取向量
        IntVector da1 = gradients.getOutputLayerGradient().getVector();
        IntVector da0 = gradients.getInputLayerGradient().getVector();
        IntVector dzWorkspace = gradients.getDzWorkspace();

        // 调用封装好的、带梯度下降的反向传播层
        BnnGradientOps.backwardGradientDescentLayer(
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