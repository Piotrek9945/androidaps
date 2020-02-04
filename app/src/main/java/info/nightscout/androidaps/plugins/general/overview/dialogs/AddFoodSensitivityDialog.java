package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.fragment.app.DialogFragment;

import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.food.EcarbBolusService;
import info.nightscout.androidaps.plugins.general.food.Food;

public class AddFoodSensitivityDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(AddFoodSensitivityDialog.class);

    private final Double SENSITIVITY_BOLUS_FACTOR_GRADE_1 = 1.0;
    private final Double SENSITIVITY_BOLUS_FACTOR_GRADE_2 = 0.9;
    private final Double SENSITIVITY_BOLUS_FACTOR_GRADE_3 = 0.8;
    private final Double SENSITIVITY_BOLUS_FACTOR_GRADE_4 = 0.7;
    private final Double SENSITIVITY_BOLUS_FACTOR_GRADE_5 = 0.6;

    private List<Food> foodList;

    private RadioGroup sensitivityRadioGroup;

    //one shot guards
    private boolean okClicked;

    public AddFoodSensitivityDialog(List<Food> foodList) {
        this.foodList = foodList;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_addfood_sensitivity_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        sensitivityRadioGroup = view.findViewById(R.id.addfood_sensitivity);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);

        //recovering state if there is something
        if (savedInstanceState != null) {
        }
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle carbsDialogState) {
        super.onSaveInstanceState(carbsDialogState);
    }


    @Override
    public synchronized void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                submit();
                break;
            case R.id.cancel:
                dismiss();
                break;
        }
    }

    private void submit() {
        if (okClicked) {
            log.debug("guarding: ok already clicked");
            dismiss();
            return;
        }
        okClicked = true;
        try {
            TempTarget tt = TreatmentsPlugin.getPlugin().getTempTargetFromHistory();
            setSensitivityFactor(foodList);
            EcarbBolusService.generateTreatment(getContext(), foodList);
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    private void setSensitivityFactor(List<Food> foodList) {
        Double sensitivityFactor = getSensitivityFactor();
        for (Food food : foodList) {
            food.sensitivityFactor = sensitivityFactor;
        }
    }

    private Double getSensitivityFactor() {
        switch (sensitivityRadioGroup.getCheckedRadioButtonId()) {
            case R.id.sensitivity_bolus_factor_grade_1: return SENSITIVITY_BOLUS_FACTOR_GRADE_1;
            case R.id.sensitivity_bolus_factor_grade_2: return SENSITIVITY_BOLUS_FACTOR_GRADE_2;
            case R.id.sensitivity_bolus_factor_grade_3: return SENSITIVITY_BOLUS_FACTOR_GRADE_3;
            case R.id.sensitivity_bolus_factor_grade_4: return SENSITIVITY_BOLUS_FACTOR_GRADE_4;
            case R.id.sensitivity_bolus_factor_grade_5: return SENSITIVITY_BOLUS_FACTOR_GRADE_5;
            default: throw new NullPointerException();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }
}
