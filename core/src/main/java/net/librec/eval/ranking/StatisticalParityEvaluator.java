package net.librec.eval.ranking;

import com.google.common.collect.BiMap;
import net.librec.data.DataModel;
import net.librec.data.convertor.appender.ItemFeatureAppender;
import net.librec.data.convertor.appender.UserFeatureAppender;
import net.librec.eval.AbstractRecommenderEvaluator;
import net.librec.math.structure.SparseMatrix;
import net.librec.recommender.item.ItemEntry;
import net.librec.recommender.item.RecommendedList;

import java.util.List;
import java.util.Set;

public class StatisticalParityEvaluator extends AbstractRecommenderEvaluator {

    /**
     * item feature matrix - indicating an item is associated to certain feature or not
     */
    protected SparseMatrix itemFeatureMatrix;

    public void setDataModel(DataModel datamodel) {
        super.setDataModel(datamodel);

    }
    /**
     * Evaluate on the test set with the the list of recommended items.
     *
     * @param testMatrix
     *            the given test set
     * @param recommendedList
     *            the list of recommended items
     * @return evaluate result
     *         (number of protected items / protected group size) /
     *         (number of unprotected items / unprotected group size )
     */

    public double evaluate(SparseMatrix testMatrix, RecommendedList recommendedList) {

        /**
         * construct protected and unprotected item set
         */
        itemFeatureMatrix = ((UserFeatureAppender) getDataModel().getFeatureAppender()).getUserFeature();

        double totalProtected = 0.0;
        double totalUnprotected = 0.0;
        int numUsers = testMatrix.numRows();
        BiMap<String, Integer> itemMapping = getDataModel().getItemMappingData();

        /**
         * count number of protected and unprotected items in data set
         */
        int numItems = itemFeatureMatrix.numRows();
        int protectedSize = 0;
        int unprotectedSize = 0;
        for (int item = 0; item < numItems; item++) {
            if (itemFeatureMatrix.getColumnsSet(item).size() > 0) {
                if (itemFeatureMatrix.get(item,0) > 0) {
                    unprotectedSize++;
                }
                else {
                   protectedSize++;
                }
            }
        }


        for (int userID = 0; userID < numUsers; userID++) {
            Set<Integer> testSetByUser = testMatrix.getColumnsSet(userID);
            if (testSetByUser.size() > 0) {
		        int unprotectedNum = 0;
                int protectedNum = 0;
                List<ItemEntry<Integer, Double>> recommendListByUser = recommendedList.getItemIdxListByUserIdx(userID);

                // calculate rate
                int topK = this.topN <= recommendListByUser.size() ? this.topN : recommendListByUser.size();
                for (int indexOfItem = 0; indexOfItem < topK; indexOfItem++) {
                    int itemID = recommendListByUser.get(indexOfItem).getKey();
                    if (itemFeatureMatrix.getColumnsSet(itemID).size() > 0) {
                        if (itemFeatureMatrix.get(itemID,0) > 0) {
                            unprotectedNum++;
                        }
                        else {
                            protectedNum++;
                        }
                    }
                }

                totalProtected += protectedNum;
                totalUnprotected += unprotectedNum;
            }
        }

        // (number of protected items / protected group size) /
        // (number of unprotected items / unprotected group size )
        double protectedRatio =  (totalProtected / protectedSize);
        double unprotectedRatio = (totalUnprotected / unprotectedSize);
        double relativeChance = protectedRatio / unprotectedRatio;
        System.out.println(relativeChance);
        return relativeChance;

    }
}
