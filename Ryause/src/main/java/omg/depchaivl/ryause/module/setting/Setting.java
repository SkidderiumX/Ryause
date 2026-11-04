package omg.depchaivl.ryause.module.setting;

public class Setting {
    public String name;
    public boolean saved = true;
    /** shown in the click GUI only while this returns true; null = always shown */
    public java.util.function.BooleanSupplier visibility;

    public Setting(String name) {
        this.name = name;
    }

    public Setting notSaved() {
        this.saved = false;
        return this;
    }

    public String getName() {
        return this.name;
    }

    public boolean isVisible() {
        return this.visibility == null || this.visibility.getAsBoolean();
    }
}
