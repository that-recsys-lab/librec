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
import net.librec.recommender.item.RecommendedList;
import net.librec.recommender.item.UserItemRatingEntry;

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

        // set up iterator for recommended items list
        Iterator<UserItemRatingEntry> recommendedEntryIter = recommendedList.entryIterator();
        recommendedList.userIterator();

        // initialize list for unique items in recommended items list
        List<Integer> uniqueItemsRecList = new java.util.ArrayList<Integer>();
        List<Integer> uniqueItemsInTestMatrix = new java.util.ArrayList<Integer>();

        int numUsers = testMatrix.numRows();
        // create a unique list of items from the test matrix
        for (int userID = 0; userID < numUsers; userID++) {
            Set<Integer> testSetByUser = testMatrix.getColumnsSet(userID);
            for (int itemId: testSetByUser) {
                if (uniqueItemsInTestMatrix.size() == 0 || !uniqueItemsInTestMatrix.contains(itemId)) {
                    uniqueItemsInTestMatrix.add(itemId);
                }
            }
        }


        // iterate through recommended items list, add unique items in testMatrix found
        while (recommendedEntryIter.hasNext()) {
            UserItemRatingEntry userItemRatingEntry = recommendedEntryIter.next();
            if (uniqueItemsRecList.size() == 0 || !uniqueItemsRecList.contains(userItemRatingEntry.getItemIdx())) {
                uniqueItemsRecList.add(userItemRatingEntry.getItemIdx());
            }
        }

        // return ratio of unique items in recommended list to number of unique items in testMatrix
        return uniqueItemsInTestMatrix.size() > 0 ? uniqueItemsRecList.size() * 1.0 / uniqueItemsInTestMatrix.size() : 0.0d;
    }
}
