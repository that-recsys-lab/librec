package net.librec.eval.ranking;

import com.google.common.collect.BiMap;
import net.librec.data.DataModel;
import net.librec.data.FeatureAppender;
import net.librec.eval.AbstractRecommenderEvaluator;
import net.librec.math.structure.SparseMatrix;
import net.librec.recommender.item.ItemEntry;
import net.librec.recommender.item.RecommendedList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.Set;

public class StatisticalParityEvaluator extends AbstractRecommenderEvaluator {

    /** LOG */
    protected final Log LOG = LogFactory.getLog(this.getClass());

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
        itemFeatureMatrix = getDataModel().getFeatureAppender().getItemFeatures();

        BiMap<String, Integer> featureIdMapping = getDataModel().getFeatureAppender().getItemFeatureMap();

        double totalProtected = 0.0;
        double totalUnprotected = 0.0;
        int numUsers = testMatrix.numRows();

        int numItems = itemFeatureMatrix.numRows();
        int numFeatures = itemFeatureMatrix.numColumns();
        String outerProtectedId = "protected";
        String outerUnprotectedId = "unprotected";
        int protectedSize = 0;
        int unprotectedSize = 0;

        // Count number of protected and unprotected items in data set
        for (int itemId = 0; itemId < numItems; itemId++) {
            for (int featureId = 0; featureId < numFeatures; featureId ++) {
                if (itemFeatureMatrix.get(itemId, featureId) == 1) {
                    if (featureId == featureIdMapping.get(outerProtectedId)) {
                        protectedSize++;
                    } else if (featureId == featureIdMapping.get(outerUnprotectedId)) {
                        unprotectedSize++;
                    } else {
                        LOG.info("StatisticalParityEvaluator: undefined inner feature id mapping");
                    }
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
                        if (itemFeatureMatrix.get(itemID, featureIdMapping.get(outerProtectedId)) == 1) {
                            protectedNum++;
                        } else if (itemFeatureMatrix.get(itemID, featureIdMapping.get(outerUnprotectedId)) == 1) {
                            unprotectedNum++;
                        } else {
                            LOG.info("StatisticalParityEvaluator: undefined feature id mapping");
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
