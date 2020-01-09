package info.nightscout.androidaps.plugins.general.food;

public class Nutrition {

    private Integer carbs;
    private Integer eCarbs;

    public Nutrition(Integer carbs, Integer eCarbs) {
        this.carbs = carbs;
        this.eCarbs = eCarbs;
    }

    public Integer getCarbs() {
        return carbs;
    }

    public void setCarbs(Integer carbs) {
        this.carbs = carbs;
    }

    public Integer getECarbs() {
        return eCarbs;
    }

    public void setECarbs(Integer eCarbs) {
        this.eCarbs = eCarbs;
    }
}
