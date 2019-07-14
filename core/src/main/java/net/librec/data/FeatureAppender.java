package net.librec.data;

import com.google.common.collect.BiMap;
import net.librec.math.structure.SparseMatrix;

public interface FeatureAppender extends DataModel {
    public int getUserFeatureId();
    public int getItemFeatureId();
    public BiMap<String, Integer> getUserFeatureMap();
    public BiMap<String, Integer> getItemFeatureMap();
    public SparseMatrix getUserFeature();
    public SparseMatrix getItemFeature();
}
