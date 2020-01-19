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
 * provider-side fairness
 */


public class DiscountedProportionalPFairnessEvaluator extends AbstractRecommenderEvaluator {

    /**
     * item feature matrix - indicating an item is associated to certain feature or not
     */
    protected SparseMatrix itemFeatureMatrix;

    /**
     * Evaluate on the test set with the the list of recommended items.
     *
     * @param testMatrix      the given test set
     * @param recommendedList the list of recommended items
     * @return evaluate result
     */

    /*
    I have to return the dpf for each group.
    How do I know how many groups I have?

     */
    public double evaluate(SparseMatrix testMatrix, RecommendedList recommendedList) {

        itemFeatureMatrix = getDataModel().getFeatureAppender().getItemFeatures();
        BiMap<String, Integer> featureIdMapping = getDataModel().getFeatureAppender().getItemFeatureMap();

        int numItems = itemFeatureMatrix.numRows();
        int numFeatures = itemFeatureMatrix.numColumns();
        int numUsers = testMatrix.numRows();

        // initialize with zeros.
        List<Double> itemFeatureDCGs = new ArrayList<>(Collections.nCopies(numFeatures + 1,0.0));

        String outerFeatureId = "";
        if (conf != null && StringUtils.isNotBlank(conf.get("data.feature"))) {
            outerFeatureId = conf.get("data.feature");
        }


        // we calcualte the dcg for all the items in a specific group that has been gained from all the users.
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
                    dcg = 1 / Maths.log(rank + 1, 2);

                    // check to see which group our item belongs to
                    for (int featureId = 0; featureId < numFeatures; featureId ++) {
                        if (itemFeatureMatrix.get(itemID, featureId) == 1) { // why do we check this in the first place?
                            itemFeatureDCGs.set(featureId, itemFeatureDCGs.get(featureId) + dcg); // is this correct? item id/item index
//                            if (featureId == featureIdMapping.get(outerFeatureId)) {
//                                itemFeatureDCGs.set(featureId, itemFeatureDCGs.get(featureId) + dcg);
//                            } else {
//                                itemFeatureDCGs.set(featureId, itemFeatureDCGs.get(featureId) + dcg);
//                            }
                        }
                    }
                }
            }
        }


        // after iterating over all the users we calculate average dcg of one group over all the others
        double dpf = 0.0;
        for (int featureId = 0; featureId < numFeatures; featureId ++) {
            String f = featureIdMapping.inverse().get(featureId);
            double fDCG = itemFeatureDCGs.get(featureId);
            System.out.println(f);
            System.out.println(fDCG);

            double sumDCG = 0.0;
            for (int fId = 0; fId < numFeatures; fId ++)  {
                if (fId != featureId) {
                    sumDCG += itemFeatureDCGs.get(fId);
                }
            }
            dpf += Maths.log((fDCG/sumDCG), 2);
        }

        return dpf;
        // ISSUE: in this way everything will be zero if we have only two groups!! think carefully dude!
        //question: should I divide it by the number of items in each group for normalization purposes? so they are comparable?!
    }
}
