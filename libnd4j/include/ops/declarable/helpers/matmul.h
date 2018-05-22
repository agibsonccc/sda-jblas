//
// Created by raver119 on 20.12.17.
//

#ifndef LIBND4J_HELPERS_MATMUL_H
#define LIBND4J_HELPERS_MATMUL_H

#include <NDArray.h>
#include <helpers/BlasHelper.h>

namespace nd4j {
    namespace ops {
        namespace helpers {
            template <typename T>
            void _matmul(NDArray<T> *A, NDArray<T> *B, NDArray<T> *C, int transA, int transB, T alpha = 1., T beta = 0.);
        }
    }
}

#endif //LIBND4J_MATMUL_H
