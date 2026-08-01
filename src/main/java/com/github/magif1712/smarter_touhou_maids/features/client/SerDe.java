package com.github.magif1712.smarter_touhou_maids.features.client;



import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 序列化与反序列化工具类 (Serializer/Deserializer)。
 * 负责 Model 的保存与加载，提供默认路径支持。
 */
public class SerDe {
    /**
     * 默认模型保存路径
     */
    public static final Path DEFAULT_MODEL_PATH = Paths.get("smarter_maids", "model");

    /**
     * 使用默认路径保存模型。
     */
    // public static void saveModel(Model model) {
    //     if (model != null) {
    //         model.save(DEFAULT_MODEL_PATH.toAbsolutePath().toString());
    //     }
    // }

    /**
     * 使用默认路径加载模型。
     */
    // public static Model loadModel() {
    //     try {
    //         return Model.loadFromFile(DEFAULT_MODEL_PATH.toAbsolutePath().toString());
    //     } catch (Exception e) {
    //         return null;
    //     }
    // }
}