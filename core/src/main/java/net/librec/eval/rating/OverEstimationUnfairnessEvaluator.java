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
package net.librec.eval.rating;

import com.google.common.collect.BiMap;
import net.librec.eval.AbstractRecommenderEvaluator;
import net.librec.math.structure.SparseMatrix;
import net.librec.recommender.item.ItemEntry;
import net.librec.recommender.item.RecommendedList;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Overestimation Unfairness Evaluator is based on the method proposed by
 * Sirui Yao and Bert Huang, <strong> Beyond Parity: Fairness Objective for Collaborative Filtering</strong>, NIPS 2017 <br>
 *
 * consumer-side fairness
 *
 * It is important in settings where users may be overwhelmed by recommendations, so too many recoms becomes detrimental.
 *
 * @author Nasim Sonboli
 */


public class OverEstimationUnfairnessEvaluator extends AbstractRecommenderEvaluator {

    /**
     * item feature matrix - indicating an item is associated to a certain feature or not
     */
    protected SparseMatrix userFeatureMatrix;
    protected double overEstimationUnfairness;

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

        if (testMatrix == null) {
            return 0.0;
        }

        //protected users
        String protectedAttr = "";
        if (conf != null && StringUtils.isNotBlank(conf.get("data.protected.feature"))) {
            protectedAttr = conf.get("data.protected.feature");
        }

        userFeatureMatrix = getDataModel().getFeatureAppender().getUserFeatures();
        BiMap<String, Integer> featureIdMapping = getDataModel().getFeatureAppender().getUserFeatureMap();


        int numUsers = userFeatureMatrix.numRows();
        int numFeatures = userFeatureMatrix.numColumns();
        int numItems = testMatrix.numColumns();

        // to avoid getting NaN for cases when no rating or predicted-rating is present, we set the counters to 1.
        List<Double> testItemRatingSumByProUsers = new ArrayList<>(Collections.nCopies(numItems,0.0));
        List<Double> testProUsersCounter = new ArrayList<>(Collections.nCopies(numItems,1.0));

        List<Double> testItemRatingSumByUnproUsers = new ArrayList<>(Collections.nCopies(numItems,0.0));
        List<Double> testUnproUsersCounter = new ArrayList<>(Collections.nCopies(numItems,1.0));

        List<Double> recItemRatingSumByProUsers = new ArrayList<>(Collections.nCopies(numItems,0.0));
        List<Double> recProUsersCounter = new ArrayList<>(Collections.nCopies(numItems,1.0));

        List<Double> recItemRatingSumByUnproUsers = new ArrayList<>(Collections.nCopies(numItems,0.0));
        List<Double> recUnproUsersCounter = new ArrayList<>(Collections.nCopies(numItems,1.0));

        for (int userID = 0; userID < numUsers; userID++) {
            // is the user from the protected group or not
            boolean protectedOrNot = false;
            for (int featureId = 0; featureId < numFeatures; featureId++) {
                if (userFeatureMatrix.get(userID, featureId) == 1) {
                    if (featureId == featureIdMapping.get(protectedAttr)) {
                        protectedOrNot = true;
                    } else {
                        protectedOrNot = false;
                    }
                }
            }

            Set<Integer> testSetByUser = testMatrix.getColumnsSet(userID);
            if (testSetByUser.size() > 0) {
                List<ItemEntry<Integer, Double>> recommendListByUser = recommendedList.getItemIdxListByUserIdx(userID);

                for (int itemId: testSetByUser) {
                    double rating = testMatrix.get(userID, itemId);
                    if (protectedOrNot) {
                        testItemRatingSumByProUsers.set(itemId, testItemRatingSumByProUsers.get(itemId) + rating);
                        testProUsersCounter.set(itemId, testProUsersCounter.get(itemId) + 1.0);
                    } else {
                        testItemRatingSumByUnproUsers.set(itemId, testItemRatingSumByUnproUsers.get(itemId) + rating);
                        testUnproUsersCounter.set(itemId, testUnproUsersCounter.get(itemId) + 1.0);
                    }
                }

                int topK = this.topN <= recommendListByUser.size() ? this.topN : recommendListByUser.size(); // topK or all the recommended items?
                for (int indexOfItem=0; indexOfItem < topK; indexOfItem++) {

                    int itemId = recommendListByUser.get(indexOfItem).getKey();
                    double ratingPredicted = recommendListByUser.get(indexOfItem).getValue();

                    if (protectedOrNot) {
                        recItemRatingSumByProUsers.set(itemId, recItemRatingSumByProUsers.get(itemId) + ratingPredicted);
                        recProUsersCounter.set(itemId, recProUsersCounter.get(itemId) + 1.0);
                    } else {
                        recItemRatingSumByUnproUsers.set(itemId, recItemRatingSumByUnproUsers.get(itemId) + ratingPredicted);
                        recUnproUsersCounter.set(itemId, recUnproUsersCounter.get(itemId) + 1.0);
                    }
                }
            }
        }

        overEstimationUnfairness = 0.0;
        double avgRatingPro = 0.0, avgRecPro = 0.0, avgRatingUnpro = 0.0, avgRecUnpro = 0.0;
        // iterate through the items that has been recommended to each user
        for (int indexOfItem = 0; indexOfItem < numItems; indexOfItem++) {

            // some of the items are not rated at all by the protected group and some are not recommended
            avgRecPro = recItemRatingSumByProUsers.get(indexOfItem) / recProUsersCounter.get(indexOfItem);
            avgRatingPro = testItemRatingSumByProUsers.get(indexOfItem) / testProUsersCounter.get(indexOfItem);

            avgRecUnpro = recItemRatingSumByUnproUsers.get(indexOfItem) / recUnproUsersCounter.get(indexOfItem);
            avgRatingUnpro = testItemRatingSumByUnproUsers.get(indexOfItem) / testUnproUsersCounter.get(indexOfItem);

            overEstimationUnfairness += Math.abs(Math.max(0.0, avgRecPro - avgRatingPro) - Math.max(0.0, avgRecUnpro - avgRatingUnpro));
        }
        // taking average over all the items
        overEstimationUnfairness /= numItems;
        return overEstimationUnfairness;
    }
}
