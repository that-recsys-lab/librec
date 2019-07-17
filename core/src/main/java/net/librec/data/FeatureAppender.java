package net.librec.data;

import com.google.common.collect.BiMap;
import net.librec.math.structure.SparseMatrix;

import java.io.IOException;

public interface FeatureAppender extends DataModel {

    /**
     * Process appender data.
     *
     * @throws IOException if I/O error occurs
     */
    public void processData() throws IOException;

    /**
     *
     *
     * @return user feature id
     */
    public int getUserFeatureId(String item, int feature);

    /**
     * @return item feature id
     */
    public int getItemFeatureId(String item, int feature);

    /**
     * Get item mapping data.
     *
     * @return  the item {raw id, inner id} map of data model.
     */
//    public BiMap<String, Integer> getUserFeatureMap();

    /**
     * Get item mapping data.
     *
     * @return  the item {raw id, inner id} map of data model.
     */
//    public BiMap<String, Integer> getItemFeatureMap();

    /**
     * @return user x feature values
     */
//    public SparseMatrix getUserFeature();

    /**
     * @return item x feature values
     */
//    public SparseMatrix getItemFeature();

    public void setUserFeatureMap(BiMap<String, Integer> userMappingData);

    public void setItemFeatureMap(BiMap<String, Integer> itemMappingData);
}
