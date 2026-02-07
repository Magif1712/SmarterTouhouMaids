#include <deque>
#include <functional>
#include <memory>
#include <random>
#include <stdexcept>
#include <thread>
#include <utility>
#include <vector>

/*
该类为无隐藏层回环神经网络

无隐藏层回环神经网络：
该类神经网络只有输入和输出层，没有隐藏层，回环指的是该神经网络训练时梯度可以从输入层重新传回输出层，形成输出层的->输入层->输出层的梯度传播回环循环。

关键原理：
如果输入层直接以对角轮间权重矩阵连接下一轮训练的输出层，那输出层的目标向量就有两个了：
一个是由训练集提供的实际目标向量；
另一个是由上一轮训练传来的梯度所产生的目标向量。
很明显这样子是自相矛盾的，所以该神经网络有一个机制解决这种矛盾：
该神经网络的输入层与输出层的神经元向量除了对应着训练集的实际输入与目标向量的部分之外，还有一个部分专门处理轮间的梯度传播，在此称这一部分为“承”部分，而相对的除此之外的另一部分则称为“启”部分。
该机制的具体内容：
为了确保输入的训练集真实性，由输出层出发的轮间权重线不能连接到输入层的启部分，包括由输出层承部分出发的。
为了确保神经网络的简美性，由输出层出发的轮间权重线只能连接与其出发神经元并排的神经元。
于是输出层的目标向量就唯一确定下来了，而且由此容易看出来该神经网络的输入层神经元向量与输出层神经元向量的维数是相等的。

该神经网络有以下关键属性：
输入值向量；
输出值向量、输出偏置向量；
启部分向量维数、承部分向量维数；
层间权重矩阵、轮间权重矩阵。

设计：
我打算把启部分放在低索引端，承部分放在高索引端。

对应表：
承上    承    C
启下    启    Q
*/

class BNN
{
public:
    BNN(int Q_size, int C_size, double learning_rate);
    void train(std::vector<std::vector<double>> &inputs, std::vector<std::vector<double>> &targets, int epochs, int batch_size, double lambda);
    std::vector<double> predict(const std::vector<double> &input);

private:
    int Q_size;
    int C_size;
    int total_size;
    double learning_rate;
    std::vector<double> input_values;
    std::vector<double> output_values;
    std::vector<double> output_biases;
    std::vector<std::vector<double>> layer_weights;
    std::vector<double> recurrent_weights;
};
