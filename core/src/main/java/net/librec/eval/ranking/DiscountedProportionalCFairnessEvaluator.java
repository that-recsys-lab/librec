/**
 * Copyright (C) 2016 LibRec
 * <p>
 * This file is part of LibRec.
 * LibRec is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * LibRec is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with LibRec. If not, see <http://www.gnu.org/licenses/>.
 */
package net.librec.eval.ranking;

import com.google.common.collect.BiMap;
import net.librec.eval.AbstractRecommenderEvaluator;
import net.librec.math.algorithm.Maths;
import net.librec.math.structure.SparseMatrix;
import net.librec.recommender.item.ItemEntry;
import net.librec.recommender.item.RecommendedList;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Discounted Proportional Fairness based on NormalizedDCGEvaluator @topN
 *
 * Kelly, F. P., Maulloo, A. K., & Tan, D. K. (1998).
 * Rate control for communication networks: shadow prices, proportional fairness and stability.
 * Journal of the Operational Research society, 49(3), 237-252.
 *
 * @author Nasim Sonboli extending the the implementation of WangYuFeng on NDCG
 *
 * consumer-side fairness
 */


public class DiscountedProportionalCFairnessEvaluator extends AbstractRecommenderEvaluator {

    /**
     * item feature matrix - indicating an item is associated to certain feature or not
     */
    protected SparseMatrix userFeatureMatrix;

    /**
     * Evaluate on the test set with the the list of recommended items.
     *
     * @param testMatrix
     *            the given test set
     * @param recommendedList
     *            the list of recommended items
     * @return evaluate result
     */


    public double evaluate(SparseMatrix testMatrix, RecommendedList recommendedList) {

        userFeatureMatrix = getDataModel().getFeatureAppender().getUserFeatures();
        BiMap<String, Integer> featureIdMapping = getDataModel().getFeatureAppender().getUserFeatureMap();

        int numUsers = userFeatureMatrix.numRows();
        int numFeatures = userFeatureMatrix.numColumns();

        // initialize with zeros.
        List<Double> userFeatureDCGs = new ArrayList<>(Collections.nCopies(numFeatures + 1,0.0));

        //protected users
        String protectedAttribute = "";
        if (conf != null && StringUtils.isNotBlank(conf.get("data.protected.feature"))) {
            protectedAttribute = conf.get("data.protected.feature");
        }


        for (int userID = 0; userID < numUsers; userID++) {
            Set<Integer> testSetByUser = testMatrix.getColumnsSet(userID);
            if (testSetByUser.size() > 0) {

                double dcg = 0.0;
                List<ItemEntry<Integer, Double>> recommendListByUser = recommendedList.getItemIdxListByUserIdx(userID);

                // calculate DCG
                int topK = this.topN <= recommendListByUser.size() ? this.topN : recommendListByUser.size();
                for (int indexOfItem = 0; indexOfItem < topK; indexOfItem++) {
                    int itemID = recommendListByUser.get(indexOfItem).getKey();
                    if (!testSetByUser.contains(itemID)) {
                        continue;
                    }
                    int rank = indexOfItem + 1;
                    dcg += 1 / Maths.log(rank + 1, 2);
                }


                // Does user belongs to the protected group or not
                for (int featureId = 0; featureId < numFeatures; featureId ++) {
                    if (userFeatureMatrix.get(userID, featureId) == 1) {
                        if (featureId == featureIdMapping.get(protectedAttribute)) {
                            userFeatureDCGs.set(featureId, userFeatureDCGs.get(featureId) + dcg);
                        } else {
                            userFeatureDCGs.set(featureId, userFeatureDCGs.get(featureId) + dcg);
                        }
                    }
                }
            }
        }


        double dpf = 0.0;
        for (int featureId = 0; featureId < numFeatures; featureId ++) {
//            String f = featureIdMapping.inverse().get(featureId);
            double fDCG = userFeatureDCGs.get(featureId);
            double sumDCG = 0.0;
            for (int fId = 0; fId < numFeatures; fId ++)  {
                sumDCG += userFeatureDCGs.get(fId);
            }
            dpf += Maths.log((fDCG/sumDCG), 2);
        }
        return dpf;
    }
}
