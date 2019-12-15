package info.nightscout.androidaps.plugins.general.food;

import android.content.Context;
import android.text.Html;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import com.google.common.base.Joiner;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.QuickWizardEntry;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.overview.dialogs.AddFoodPercentDialog;
import info.nightscout.androidaps.utils.BolusWizard;
import info.nightscout.androidaps.utils.OKDialog;

public class EcarbBolusService {

    public static final double ACCURATE_COEFFICIENT = 0.9;

    public EcarbBolusService() {}

    public static void generateTreatmentWithSummary(Context context, FragmentManager manager, List<Food> foodList) {
        if (foodList.size() > 0) {
            List<String> actions = new LinkedList<>();
            for (Food food : foodList) {
                String text = "";
                text = text.concat(food.name);
                text = text.concat(", " + FoodUtils.Companion.formatFloatToDisplay(food.portion * food.portionCount) + " " + food.units);
                text = text.concat(", eCarbs: " + "<font color='" + MainApp.gc(R.color.carbs) + "'>" + FoodUtils.Companion.roundDoubleToInt(EcarbService.Companion.calculateEcarbs(food)) + "</font>");
                text = text.concat(", Węglow.: " + "<font color='" + MainApp.gc(R.color.colorCalculatorButton) + "'>" + FoodUtils.Companion.roundDoubleToInt(BolusService.Companion.calculateCarb(food)) + "</font>");
                actions.add(text);
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            setDialogTitle(builder, isDefaultCorrectionFactor(foodList));
            builder.setMessage(Html.fromHtml(Joiner.on("<br/>").join(actions)));
            builder.setPositiveButton(MainApp.gs(R.string.ok), (dialog, id) -> {
                synchronized (builder) {
                    EcarbBolusService.generateTreatment(context, foodList);
                }
            });
            builder.setNeutralButton("KOREKTA", (dialog, id) -> {
                synchronized (builder) {
                    showAddFoodPercent(manager, foodList);
                }
            });
            builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
            builder.show();
        }
    }

    private static void setDialogTitle(AlertDialog.Builder builder, boolean isDefaultCorrectionFactor) {
        if (isDefaultCorrectionFactor) {
            builder.setTitle("Lista posiłków");
        } else {
            builder.setTitle("Lista posiłków (korekta)");
        }
    }

    public static void showAddFoodPercent(FragmentManager manager, List<Food> foodList) {
        new AddFoodPercentDialog(foodList).show(manager, "AddFoodPercentDialog");
    }

    public static void generateTreatment(Context context, List<Food> foodList) {
        int eCarbs = EcarbService.Companion.calculateEcarbs(foodList);
        int carbs = BolusService.Companion.calculateCarb(foodList);

        if (carbs > 0) {
            generateEcarbAndBolus(context, carbs, eCarbs);
        } else {
            EcarbService.Companion.generateEcarbs(eCarbs);
        }

        FoodService.clearFoodCountAdded();
    }

    public static void generateEcarbAndBolus(Context context, int carbs, int eCarbs) {
        try {
            BolusWizard wizard = onClickQuickwizard(context, carbs);
            wizard.confirmAndExecute(context, eCarbs);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static BolusWizard onClickQuickwizard(Context context, Integer carbs) throws JSONException {
        final Profile profile = ProfileFunctions.getInstance().getProfile();
        final String profileName = ProfileFunctions.getInstance().getProfileName();
        final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();

        JSONObject input = new JSONObject("{\"buttonText\":\"\",\"carbs\":" + carbs + ",\"validFrom\":0,\"validTo\":86340, \"useBG\":1, \"useBolusIOB\":1, \"useBasalIOB\":1}");
        final QuickWizardEntry quickWizardEntry = new QuickWizardEntry(input, -1);
        if (quickWizardEntry != null && profile != null && pump != null) {
            final BolusWizard wizard = quickWizardEntry.doCalc(profile, profileName, null, true);

            if (wizard.getCalculatedTotalInsulin() > 0d && quickWizardEntry.carbs() > 0d) {
                Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(quickWizardEntry.carbs())).value();

                if (Math.abs(wizard.getInsulinAfterConstraints() - wizard.getCalculatedTotalInsulin()) >= pump.getPumpDescription().pumpType.determineCorrectBolusStepSize(wizard.getInsulinAfterConstraints()) || !carbsAfterConstraints.equals(quickWizardEntry.carbs())) {
                    OKDialog.show(context, MainApp.gs(R.string.treatmentdeliveryerror), MainApp.gs(R.string.constraints_violation) + "\n" + MainApp.gs(R.string.changeyourinput), null);
                    return wizard;
                }

                return wizard;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static boolean isDefaultCorrectionFactor(Food food) {
        if (food.correctionFactor == 1.0) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean isDefaultCorrectionFactor(List<Food> foodList) {
        for (Food food : foodList) {
            if (isDefaultCorrectionFactor(food) == false) {
                return false;
            }
        }
        return true;
    }

}
