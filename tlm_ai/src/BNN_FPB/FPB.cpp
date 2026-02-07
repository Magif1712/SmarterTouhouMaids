#include <deque>
#include <functional>
#include <memory>
#include <random>
#include <stdexcept>
#include <thread>
#include <utility>
#include <vector>

struct Shape {
  int rows;
  int cols;
};

using Matrix = std::vector<std::vector<int>>;

static Matrix zeros(int rows, int cols) {
  return Matrix(rows, std::vector<int>(cols, 0));
}

static Matrix random01(int rows, int cols, std::mt19937& rng) {
  std::uniform_int_distribution<int> dist(0, 1);
  Matrix out(rows, std::vector<int>(cols, 0));
  for (int r = 0; r < rows; ++r) {
    for (int c = 0; c < cols; ++c) {
      out[r][c] = dist(rng);
    }
  }
  return out;
}

static void ensure_same_cols(const Matrix& a, const Matrix& b) {
  if (a.empty() || b.empty()) {
    return;
  }
  if (a[0].size() != b[0].size()) {
    throw std::runtime_error("matrix column mismatch");
  }
}

static Matrix concat_rows(const Matrix& a, const Matrix& b) {
  ensure_same_cols(a, b);
  Matrix out;
  out.reserve(a.size() + b.size());
  for (const auto& row : a) {
    out.push_back(row);
  }
  for (const auto& row : b) {
    out.push_back(row);
  }
  return out;
}

static Matrix concat_rows3(const Matrix& a, const Matrix& b, const Matrix& c) {
  return concat_rows(concat_rows(a, b), c);
}

static Matrix slice_rows(const Matrix& m, int start, int end) {
  if (start < 0 || end < start || end > static_cast<int>(m.size())) {
    throw std::runtime_error("slice out of range");
  }
  Matrix out;
  out.reserve(end - start);
  for (int r = start; r < end; ++r) {
    out.push_back(m[r]);
  }
  return out;
}

static Matrix to_bool_matrix(const Matrix& m) {
  Matrix out = m;
  for (auto& row : out) {
    for (auto& v : row) {
      v = v ? 1 : 0;
    }
  }
  return out;
}

class Tensor4 {
public:
  Tensor4(int out_rows, int out_cols, int in_rows, int in_cols)
      : out_rows_(out_rows),
        out_cols_(out_cols),
        in_rows_(in_rows),
        in_cols_(in_cols),
        data_(static_cast<std::size_t>(out_rows) * static_cast<std::size_t>(out_cols) *
              static_cast<std::size_t>(in_rows) * static_cast<std::size_t>(in_cols), 0) {}

  int& at(int orow, int ocol, int irow, int icol) {
    return data_[index(orow, ocol, irow, icol)];
  }

  int at(int orow, int ocol, int irow, int icol) const {
    return data_[index(orow, ocol, irow, icol)];
  }

  int out_rows() const { return out_rows_; }
  int out_cols() const { return out_cols_; }
  int in_rows() const { return in_rows_; }
  int in_cols() const { return in_cols_; }

private:
  std::size_t index(int orow, int ocol, int irow, int icol) const {
    return (((static_cast<std::size_t>(orow) * static_cast<std::size_t>(out_cols_) +
              static_cast<std::size_t>(ocol)) * static_cast<std::size_t>(in_rows_) +
             static_cast<std::size_t>(irow)) * static_cast<std::size_t>(in_cols_) +
            static_cast<std::size_t>(icol));
  }

  int out_rows_;
  int out_cols_;
  int in_rows_;
  int in_cols_;
  std::vector<int> data_;
};

class BNN {
public:
  explicit BNN(const std::vector<Shape>& layers_shape)
      : layers_shape_(layers_shape),
        rng_(std::random_device{}()) {
    // 这个神经网络每一层神经元都是二维矩阵
    // layers是形如"[[1,2],[3,2],[5,9],[4,6]]"的神经网络形状列表，其中"[1,2]"表示第一层神经元的矩阵形状，后面的同理
    if (layers_shape_.size() != 4) {
      throw std::runtime_error("layers_shape must have 4 layers");
    }
    // 初始化权重和偏置（三维->四维权重）
    for (int i = 0; i < 3; ++i) {
      const auto& in_shape = layers_shape_[i];
      const auto& out_shape = layers_shape_[i + 1];
      Tensor4 w(out_shape.rows, out_shape.cols, in_shape.rows, in_shape.cols);
      // 权重形状：(out_rows, out_cols, in_rows, in_cols)
      // 生成 0 和 1 的随机整数（均匀分布，左闭右开区间 [low, high)）
      std::uniform_int_distribution<int> dist(0, 1);
      for (int orow = 0; orow < out_shape.rows; ++orow) {
        for (int ocol = 0; ocol < out_shape.cols; ++ocol) {
          for (int irow = 0; irow < in_shape.rows; ++irow) {
            for (int icol = 0; icol < in_shape.cols; ++icol) {
              w.at(orow, ocol, irow, icol) = dist(rng_);
            }
          }
        }
      }
      weights_.push_back(std::move(w));
      biases_.push_back(zeros(out_shape.rows, out_shape.cols));
    }
  }

  struct ForwardResult {
    std::vector<Matrix> activations;
    std::vector<Matrix> pre_activations;
  };

  // 前向传播（输入应为二维矩阵组）
  ForwardResult forward(const Matrix& x) {
    ForwardResult result;
    // 存储各层激活矩阵
    result.activations.push_back(to_bool_matrix(x));
    for (int layer = 0; layer < 3; ++layer) {
      const auto& w = weights_[layer];
      const auto& b = biases_[layer];
      const Matrix& a_in = result.activations.back();
      // 扩展激活值维度以匹配权重维度
      Matrix z(w.out_rows(), std::vector<int>(w.out_cols(), 0));
      for (int orow = 0; orow < w.out_rows(); ++orow) {
        for (int ocol = 0; ocol < w.out_cols(); ++ocol) {
          int any = 0;
          for (int irow = 0; irow < w.in_rows(); ++irow) {
            for (int icol = 0; icol < w.in_cols(); ++icol) {
              if (a_in[irow][icol] && w.at(orow, ocol, irow, icol)) {
                any = 1;
                irow = w.in_rows();
                break;
              }
            }
          }
          // 沿着输入矩阵的行和列方向进行逻辑或操作
          z[orow][ocol] = any;
        }
      }
      Matrix a_out(w.out_rows(), std::vector<int>(w.out_cols(), 0));
      for (int r = 0; r < w.out_rows(); ++r) {
        for (int c = 0; c < w.out_cols(); ++c) {
          int fz = z[r][c] <= 1 ? z[r][c] : 1;
          a_out[r][c] = (fz ^ b[r][c]) ? 1 : 0;
        }
      }
      // 存储加权和矩阵
      result.pre_activations.push_back(z);
      result.activations.push_back(a_out);
    }
    return result;
  }

  // 融合过去传来的梯度产生的新目标趋势，融合式反向传播，融合是在BNN外面进行的，
  // y_true就是融合的结果，BNN内部只需要将首层的梯度也计算出来然后处理后传出即可
  std::pair<std::vector<Matrix>, Matrix> backward_spreading_forward(const Matrix& x, const Matrix& y_true) {
    auto forward_result = forward(x);
    std::vector<Matrix>& activations = forward_result.activations;
    std::vector<Matrix>& pre_activations = forward_result.pre_activations;

    // 单样本反向传播
    // 注意：此处不除以 batch_size
    Matrix delta_a = activations.back();
    for (int r = 0; r < static_cast<int>(delta_a.size()); ++r) {
      for (int c = 0; c < static_cast<int>(delta_a[r].size()); ++c) {
        delta_a[r][c] = delta_a[r][c] - y_true[r][c];
      }
    }

    for (int layer = 2; layer >= 0; --layer) {
      const Matrix& pre = pre_activations[layer];
      const Matrix& bias = biases_[layer];
      Matrix df(pre.size(), std::vector<int>(pre[0].size(), 0));
      Matrix fpre(pre.size(), std::vector<int>(pre[0].size(), 0));
      for (int r = 0; r < static_cast<int>(pre.size()); ++r) {
        for (int c = 0; c < static_cast<int>(pre[r].size()); ++c) {
          fpre[r][c] = pre[r][c] <= 1 ? pre[r][c] : 1;
          df[r][c] = pre[r][c] <= 1 ? 1 : 0;
        }
      }

      Matrix grad_b(pre.size(), std::vector<int>(pre[0].size(), 0));
      Matrix term(pre.size(), std::vector<int>(pre[0].size(), 0));
      for (int r = 0; r < static_cast<int>(pre.size()); ++r) {
        for (int c = 0; c < static_cast<int>(pre[r].size()); ++c) {
          int fpre_minus_b = fpre[r][c] - bias[r][c];
          // 计算单样本梯度
          grad_b[r][c] = -delta_a[r][c] * fpre_minus_b;
          term[r][c] = delta_a[r][c] * fpre_minus_b * df[r][c];
        }
      }

      const Matrix& a_prev = activations[layer];
      Tensor4 grad_w(weights_[layer].out_rows(), weights_[layer].out_cols(),
                     weights_[layer].in_rows(), weights_[layer].in_cols());
      for (int orow = 0; orow < weights_[layer].out_rows(); ++orow) {
        for (int ocol = 0; ocol < weights_[layer].out_cols(); ++ocol) {
          for (int irow = 0; irow < weights_[layer].in_rows(); ++irow) {
            for (int icol = 0; icol < weights_[layer].in_cols(); ++icol) {
              grad_w.at(orow, ocol, irow, icol) = a_prev[irow][icol] * term[orow][ocol];
            }
          }
        }
      }

      Matrix delta_next(weights_[layer].in_rows(), std::vector<int>(weights_[layer].in_cols(), 0));
      for (int irow = 0; irow < weights_[layer].in_rows(); ++irow) {
        for (int icol = 0; icol < weights_[layer].in_cols(); ++icol) {
          int sum = 0;
          for (int orow = 0; orow < weights_[layer].out_rows(); ++orow) {
            for (int ocol = 0; ocol < weights_[layer].out_cols(); ++ocol) {
              sum += term[orow][ocol] * weights_[layer].at(orow, ocol, irow, icol);
            }
          }
          delta_next[irow][icol] = sum;
        }
      }
      // 更新 delta_a 用于下一层，由于要为梯度融合做准备，所以输入层的delta_a也要算
      delta_a = std::move(delta_next);

      // 参数更新
      for (int orow = 0; orow < weights_[layer].out_rows(); ++orow) {
        for (int ocol = 0; ocol < weights_[layer].out_cols(); ++ocol) {
          for (int irow = 0; irow < weights_[layer].in_rows(); ++irow) {
            for (int icol = 0; icol < weights_[layer].in_cols(); ++icol) {
              double updated = static_cast<double>(weights_[layer].at(orow, ocol, irow, icol)) -
                               static_cast<double>(grad_w.at(orow, ocol, irow, icol));
              weights_[layer].at(orow, ocol, irow, icol) = updated <= 0.5 ? 0 : 1;
            }
          }
        }
      }

      if (layer != 2) {
        // 输出层的b必须恒为0
        for (int r = 0; r < static_cast<int>(biases_[layer].size()); ++r) {
          for (int c = 0; c < static_cast<int>(biases_[layer][r].size()); ++c) {
            double updated = static_cast<double>(biases_[layer][r][c]) - static_cast<double>(grad_b[r][c]);
            biases_[layer][r][c] = updated <= 0.5 ? 0 : 1;
          }
        }
      }
    }

    // 计算传出目标趋势并return，顺便将推理时的运算结果return出来
    Matrix y_goal_merge_out = x;
    for (int r = 0; r < static_cast<int>(x.size()); ++r) {
      for (int c = 0; c < static_cast<int>(x[r].size()); ++c) {
        double v = static_cast<double>(x[r][c]) - static_cast<double>(delta_a[r][c]);
        y_goal_merge_out[r][c] = v <= 0.5 ? 0 : 1;
      }
    }
    return {activations, y_goal_merge_out};
  }

private:
  std::vector<Shape> layers_shape_;
  std::vector<Tensor4> weights_;
  std::vector<Matrix> biases_;
  std::mt19937 rng_;
};

class FPB {
public:
  FPB(Shape C_shape, Shape J_shape, Shape X_shape,
      std::function<Matrix()> func_receptor_once,
      std::function<void(const Matrix&)> func_effector_once)
      : C_shape_(C_shape),
        H_shape_{J_shape.rows + X_shape.rows, J_shape.cols},
        J_shape_(J_shape),
        X_shape_(X_shape),
        func_receptor_once_(std::move(func_receptor_once)),
        func_effector_once_(std::move(func_effector_once)),
        rng_(std::random_device{}()) {
    if (!(H_shape_.cols == J_shape_.cols && J_shape_.cols == X_shape_.cols && X_shape_.cols == C_shape_.cols)) {
      throw std::runtime_error("shape mismatch");
    }
    layers_shape_ = {
        {C_shape_.rows + H_shape_.rows + J_shape_.rows + X_shape_.rows, J_shape_.cols},
        {C_shape_.rows + H_shape_.rows + J_shape_.rows + X_shape_.rows, J_shape_.cols},
        {C_shape_.rows + H_shape_.rows + J_shape_.rows + X_shape_.rows, J_shape_.cols},
        {C_shape_.rows + H_shape_.rows + J_shape_.rows + X_shape_.rows, J_shape_.cols}};
    S_ = std::make_unique<BNN>(layers_shape_);
    X_now_ = zeros(X_shape_.rows, X_shape_.cols);

    Matrix C = zeros(C_shape_.rows, C_shape_.cols);
    Matrix J = func_receptor_once_();
    Matrix X = zeros(X_shape_.rows, X_shape_.cols);
    H_ = concat_rows(J, X);
    push_memory(immediate_memory_, concat_rows3(H_, J, X));
    for (int i = 0; i < 2; ++i) {
      C = Cycles_L_once(C);
    }
    std::thread(&FPB::Cycles_L, this).detach();
    std::thread(&FPB::NL_KS_Cycle, this).detach();
    std::thread(&FPB::CPW_Cycle, this).detach();
    std::thread(&FPB::func_effector_Cycle, this).detach();
  }

private:
  // 第一代注释
  //     觉前行(The feeling of the future, the behavior of the past)通用人工智能系统
  //     因为创作者的习惯，称神经网络为三角形(triangle)，感觉信息为正方形(C)，行为信息为圆形(X)，
  // 另外还有一种信息，我称之为C，它负责把过去传来的梯度继承起来，对于任意一个单一时刻的这些信息的总和我称之为记忆元
  //     融合梯度的时候需要让y_goal_merge中的感觉信息与行为信息和y_true中的相同以保证实事求是、尊重事实，只有C信息会真正地传递下去
  //     "第一个"X元素为推理未来索引，"第二个"X元素为推理过去索引，它们都是二进制自然数
  //     为了兼顾精确回忆，原先只需要一个来回的combo活塞虫改为两个来回，顺序为"1向过去->2向未来->3向未来->4向过去"
  //     在一定程度上涉及模型的自蒸馏，即自己蒸馏自己，用自己对过去的认识蒸馏出对未来的预测
  // 最新代注释
  //     对应表
  // 传承	    Transmission    承 C
  // 记忆	    Memory          痕 H (从本质上看这也是所有感觉的一部分)
  // 实际感觉	Feel            觉 J
  // 实践	    Praxis          行 X (从本质上看这也是所有感觉的一部分)
  // 认识	    Cognition       识 S
  //     先是C，然后是H，然后是J，最后是X
  //     因为"承"是拿来的，"痕"是刻上的，所以
  //     对应表
  // 拿来 NL   承
  // 刻上 KS   痕
  void push_memory(std::deque<Matrix>& memory, const Matrix& value) {
    if (memory.size() == 2) {
      memory.pop_front();
    }
    memory.push_back(value);
  }

  void func_effector_Cycle() {
    while (true) {
      func_effector_once_(X_now_);
    }
  }

  Matrix Cycles_L_once(const Matrix& C_in) {
    // a_neg_1是过去的总输出
    // 准备C,H,J,X要素，C已经传入了
    Matrix H = H_;
    Matrix J = func_receptor_once_();
    Matrix& last = immediate_memory_.back();
    // "combo活塞虫"推理
    // 向未来计算输出，因为输入的X本质上算是一种感觉，向未来的话是"第一个"X元素为1，"第二个"X元素为0，所以要进行赋值
    // （事实上这是为了给可能的迭代做准备，如果索引的值拓展到整数域的话就可以由行为X控制现在的输入感觉X的向过去索引和向未来索引了）
    int x_start = H_shape_.rows + J_shape_.rows;
    last[x_start][0] = 1;
    last[x_start + 1][0] = 0;
    Matrix X_past = slice_rows(last, x_start, x_start + X_shape_.rows);
    Matrix a_neg_1 = S_->forward(concat_rows3(C_in, H, J, X_past)).activations.back();
    // 向过去计算输出，因为输入的X本质上算是一种感觉，向过去的话是"第一个"X元素为0，"第二个"X元素为1，所以要进行赋值
    int idx = C_shape_.rows + H_shape_.rows + J_shape_.rows;
    a_neg_1[idx][0] = 0;
    a_neg_1[idx + 1][0] = 1;
    a_neg_1 = S_->forward(a_neg_1).activations.back();
    // 提取出X，即为当前的行为
    X_now_ = slice_rows(a_neg_1, idx, idx + X_shape_.rows);
    // 最后再向未来计算输出
    a_neg_1[idx][0] = 1;
    a_neg_1[idx + 1][0] = 0;
    a_neg_1 = S_->forward(a_neg_1).activations.back();
    // 提取更新后的C并传出，还有更新瞬时记忆
    Matrix C_out = slice_rows(a_neg_1, 0, C_shape_.rows);
    push_memory(immediate_memory_, concat_rows3(H, J, X_now_));
    return C_out;
  }

  void Cycles_L() {
    // 初始化C
    Matrix C = zeros(C_shape_.rows, C_shape_.cols);
    // 循环运行
    while (true) {
      C = Cycles_L_once(C);
    }
  }

  std::pair<Matrix, Matrix> NL_KS_once(const Matrix& y_goal_merge, const Matrix& C) {
    // NL,拿来（承）;KS,刻上（痕）
    // NL
    // 提取传承目标值
    Matrix C_goal = slice_rows(y_goal_merge, 0, C_shape_.rows);
    // 向过去计算输出并训练，因为输入的X本质上算是一种感觉，向过去的话是"第一个"X元素为0，"第二个"X元素为1，所以要进行赋值
    Matrix immediate_memory_1_prev = concat_rows(C, immediate_memory_[1]);
    int idx = C_shape_.rows + H_shape_.rows + J_shape_.rows;
    immediate_memory_1_prev[idx][0] = 0;
    immediate_memory_1_prev[idx + 1][0] = 1;
    // 然后要把y_goal_merge中的C_goal_merge取出并融合到目标值中
    Matrix y_true = concat_rows(C_goal, immediate_memory_[0]);
    auto [activations, y_goal_merge_out] = S_->backward_spreading_forward(immediate_memory_1_prev, y_true);
    Matrix C_out = slice_rows(activations.back(), 0, C_shape_.rows);
    // KS
    H_ = slice_rows(activations.back(), C_shape_.rows + H_shape_.rows,
                    C_shape_.rows + H_shape_.rows + J_shape_.rows + X_shape_.rows);
    return {y_goal_merge_out, C_out};
  }

  void NL_KS_Cycle() {
    // 初始化y_goal_merge
    Matrix C_goal = zeros(C_shape_.rows, C_shape_.cols);
    Matrix y_goal_merge = concat_rows(C_goal, immediate_memory_[0]);
    Matrix C = zeros(C_shape_.rows, C_shape_.cols);
    // 循环运行
    while (true) {
      auto result = NL_KS_once(y_goal_merge, C);
      y_goal_merge = std::move(result.first);
      C = std::move(result.second);
    }
  }

  Matrix Cycles_R_once(const Matrix& a_in) {
    // 用来为CPW提供素材
    // 向过去计算输出并训练，因为输入的X本质上算是一种感觉，向过去的话是"第一个"X元素为0，"第二个"X元素为1，所以要进行赋值
    Matrix a_neg_1 = a_in;
    int idx = C_shape_.rows + H_shape_.rows + J_shape_.rows;
    a_neg_1[idx][0] = 0;
    a_neg_1[idx + 1][0] = 1;
    // 然后要把y_goal_merge中的C_goal_merge取出并融合到目标值中
    a_neg_1 = S_->forward(a_neg_1).activations.back();
    // 为CPW提供素材
    push_memory(immediate_memory_opposite_, slice_rows(
        a_neg_1, C_shape_.rows,
        C_shape_.rows + H_shape_.rows + J_shape_.rows + X_shape_.rows));
    return a_neg_1;
  }

  std::pair<Matrix, Matrix> CPW_once(const Matrix& C_goal_in, const Matrix& a_in) {
    // 初始化
    // 为CPW提供素材
    Matrix C_goal = C_goal_in;
    Matrix a_neg_1 = Cycles_R_once(a_in);
    // 推理顺序是"1向未来->2向过去->3向未来"则训练顺序是"1向过去->2向未来->3向过去"
    // 得准备C,H,J,X要素，
    // 由于是反向传播，所以输入层的C没有之前输出的值可以确定其数值，根据该项目的原理，此时应给输入层的C赋在{0,1}内的随机值
    Matrix& H_J_X_past = immediate_memory_opposite_[1];
    Matrix& H_J_X_present = immediate_memory_opposite_[0];

    // 向未来计算输出并训练，因为输入的X本质上算是一种感觉，向未来的话是"第一个"X元素为1，"第二个"X元素为0，所以要进行赋值
    Matrix C = random01(C_shape_.rows, C_shape_.cols, rng_);
    int idx = H_shape_.rows + J_shape_.rows;
    H_J_X_past[idx][0] = 1;
    H_J_X_past[idx + 1][0] = 0;
    // 因为目标值应求真实不该赋值，所以目标值直接用瞬时记忆里的，另外，在这里activations好像没啥用
    auto res1 = S_->backward_spreading_forward(concat_rows(C, H_J_X_past),
                                               concat_rows(C_goal, immediate_memory_opposite_[0]));
    // 提取更新后的C_goal
    C_goal = slice_rows(res1.second, 0, C_shape_.rows);

    // 向过去计算输出并训练，因为输入的X本质上算是一种感觉，向过去的话是"第一个"X元素为0，"第二个"X元素为1，所以要进行赋值
    C = random01(C_shape_.rows, C_shape_.cols, rng_);
    H_J_X_present[idx][0] = 0;
    H_J_X_present[idx + 1][0] = 1;
    // 因为目标值应求真实不该赋值，所以目标值直接用瞬时记忆里的
    auto res2 = S_->backward_spreading_forward(concat_rows(C, H_J_X_present),
                                               concat_rows(C_goal, immediate_memory_opposite_[1]));
    // 提取更新后的C_goal
    C_goal = slice_rows(res2.second, 0, C_shape_.rows);

    // 最后再向未来计算输出并训练
    C = random01(C_shape_.rows, C_shape_.cols, rng_);
    H_J_X_past[idx][0] = 1;
    H_J_X_past[idx + 1][0] = 0;
    // 因为目标值应求真实不该赋值，所以目标值直接用瞬时记忆里的
    auto res3 = S_->backward_spreading_forward(concat_rows(C, H_J_X_past),
                                               concat_rows(C_goal, immediate_memory_opposite_[0]));
    // 提取更新后的C_goal并传出，还有更新瞬时记忆
    C_goal = slice_rows(res3.second, 0, C_shape_.rows);
    return {C_goal, a_neg_1};
  }

  void CPW_Cycle() {
    // 初始化a_neg_1
    Matrix C_R_0 = zeros(C_shape_.rows, C_shape_.cols);
    Matrix a_neg_1 = concat_rows(C_R_0, immediate_memory_[1]);
    // 初始化反向瞬时记忆队列
    // 合理地填满反向瞬时记忆队列
    for (int i = 0; i < 2; ++i) {
      a_neg_1 = Cycles_R_once(a_neg_1);
    }
    // 初始化C_goal
    Matrix C_goal = zeros(C_shape_.rows, C_shape_.cols);
    while (true) {
      auto result = CPW_once(C_goal, a_neg_1);
      C_goal = std::move(result.first);
      a_neg_1 = std::move(result.second);
    }
  }

  Shape C_shape_;
  Shape H_shape_;
  Shape J_shape_;
  Shape X_shape_;
  std::vector<Shape> layers_shape_;
  std::unique_ptr<BNN> S_;
  Matrix X_now_;
  Matrix H_;
  std::function<Matrix()> func_receptor_once_;
  std::function<void(const Matrix&)> func_effector_once_;
  std::deque<Matrix> immediate_memory_;
  std::deque<Matrix> immediate_memory_opposite_;
  std::mt19937 rng_;
};
