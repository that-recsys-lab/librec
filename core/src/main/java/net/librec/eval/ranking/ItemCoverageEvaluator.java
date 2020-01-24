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

import net.librec.eval.AbstractRecommenderEvaluator;
import net.librec.math.structure.SparseMatrix;
import net.librec.recommender.item.ItemEntry;
import net.librec.recommender.item.RecommendedList;
import net.librec.recommender.item.UserItemRatingEntry;
import net.librec.util.Lists;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Finds the ratio of unique items recommended of users to total unique items in testMatrix
 *  *
 * @author Nasim Sonboli, Florencia Cabral
 */

public class ItemCoverageEvaluator extends AbstractRecommenderEvaluator {

    public double evaluate(SparseMatrix testMatrix, RecommendedList recommendedList) {

        if (testMatrix == null) {
            return 0.0;
        }

        // initialize list for unique items in recommended items list
        List<Integer> uniqueItemsRecList = new java.util.ArrayList<Integer>();
        List<Integer> uniqueItemsInTestMatrix = new java.util.ArrayList<Integer>();

        int numUsers = testMatrix.numRows();

        for (int userID = 0; userID < numUsers; userID++) {
            Set <Integer> testSetByUser = testMatrix.getColumnsSet(userID);
            if (testSetByUser.size() > 0) {

                for (int itemID: testSetByUser) {
                    if (!uniqueItemsInTestMatrix.contains(itemID)) {
                        uniqueItemsInTestMatrix.add(itemID);
                    }
                }

                List<ItemEntry<Integer, Double>> recommendListByUser = recommendedList.getItemIdxListByUserIdx(userID);
                int topK = this.topN <= recommendListByUser.size() ? this.topN : recommendListByUser.size();
                for (int indexOfItem = 0; indexOfItem < topK; indexOfItem++) {
                    int recommendItemIdx = recommendListByUser.get(indexOfItem).getKey();
                    if (!uniqueItemsRecList.contains(recommendItemIdx)) {
                        uniqueItemsRecList.add(recommendItemIdx);
                    }
                }
            }
        }

        // return ratio of unique items in recommended list to number of unique items in testMatrix
        return uniqueItemsInTestMatrix.size() > 0 ? uniqueItemsRecList.size() * 1.0 / uniqueItemsInTestMatrix.size() : 0.0d;
    }
}
