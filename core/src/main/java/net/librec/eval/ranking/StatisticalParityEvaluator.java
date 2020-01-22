package net.librec.eval.ranking;

import com.google.common.collect.BiMap;
import net.librec.data.DataModel;
import net.librec.eval.AbstractRecommenderEvaluator;
import net.librec.math.structure.SparseMatrix;
import net.librec.recommender.item.ItemEntry;
import net.librec.recommender.item.RecommendedList;
import org.apache.commons.lang.StringUtils;
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

    public void setDataModel(DataModel datamodel) { // do we need this?
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
//        BiMap<String, Integer> itemIdMapping = getDataModel().getFeatureAppender().getItemIdMap();
        // m_itemIdMap

        double totalProtected = 0.0;
        double totalUnprotected = 0.0;
        int numUsers = testMatrix.numRows();

        int numItems = itemFeatureMatrix.numRows();
        int numFeatures = itemFeatureMatrix.numColumns();
        String protectedAttribute = conf.get("data.protected.feature");
        if (conf != null && StringUtils.isNotBlank(conf.get("data.protected.feature"))) {
            protectedAttribute = conf.get("data.protected.feature");
        }
        int protectedSize = 0;
        int unprotectedSize = 0;

        // Count number of protected and unprotected items in data set
        for (int itemId = 0; itemId < numItems; itemId++) {
            for (int featureId = 0; featureId < numFeatures; featureId ++) {
                if (itemFeatureMatrix.get(itemId, featureId) == 1) {
                    if (featureId == featureIdMapping.get(protectedAttribute)) {
                        protectedSize++;
                    } else {
                        unprotectedSize++;
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
                    // Nasim: needs to be checked, itemID or itemindex
                    if (itemFeatureMatrix.getColumnsSet(itemID).size() > 0) {
                        if (itemFeatureMatrix.get(itemID, featureIdMapping.get(protectedAttribute)) == 1) {
                            protectedNum++;
                        } else {
                            unprotectedNum++;
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
        return relativeChance;
    }
}
