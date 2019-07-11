package net.librec.data;

public interface FeatureAppender extends DataModel {
    public void getUserFeatureId();
    public void getItemFeatureId();
    public void getUserFeatureMap();
    public void getItemFeatureMap();
    public void getUserFeature();
    public void getItemFeature();
}
