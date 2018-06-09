//
// @author raver119@gmail.com
//

#include <op_boilerplate.h>
#if NOT_EXCLUDED(OP_gather_list)

#include <ops/declarable/CustomOperations.h>

namespace nd4j {
    namespace ops {
        LIST_OP_IMPL(gather_list, 2, 1, 0, -2) {
            auto list = INPUT_LIST(0);
            auto indices = INPUT_VARIABLE(1);

            REQUIRE_TRUE(indices->isVector(), 0, "Indices for Gather operation should be a vector");
            REQUIRE_TRUE(list->height() > 0, 0, "Number of elements in list should be positive prior to Gather call");
            REQUIRE_TRUE(list->height() == indices->lengthOf(), 1, "Number of indicies should be equal to number of elements in list, but got [%i] indices instead", indices->lengthOf());

            // first of all we need to get shapes
            std::vector<Nd4jLong> shape({0});
            shape[0] = indices->lengthOf();
            for (int e = 0; e < list->height(); e++) {
                auto array = list->readRaw(e);

                // now we should fill other dimensions 
                if (e == 0) {
                    array->printShapeInfo("array[0] shape");
                    for (int d = 0; d < array->rankOf(); d++)
                        shape.emplace_back(array->sizeAt(d));
                }
            }

            auto result = new NDArray<T>('c', shape);
            int skipPosition = 0;
            for (int e = 0; e < indices->lengthOf(); e++) {
                auto idx = static_cast<int>(indices->getIndexedScalar(e));
                auto array = list->readRaw(idx);
                
                IndicesList indicesList;
                // first dimension
                indicesList.push_back(NDIndex::interval(skipPosition, skipPosition + 1));

                for (int d = 0; d < array->rankOf(); d++)
                    indicesList.push_back(NDIndex::all());

                auto subarray = result->subarray(indicesList);
                subarray->assign(array);

                skipPosition++;

                delete subarray;
            }


            OVERWRITE_RESULT(result);
    

            return ND4J_STATUS_OK;
        }
        DECLARE_SYN(TensorArrayGatherV3, gather_list);
        DECLARE_SYN(tensorarraygatherv3, gather_list);
    }
}

#endif