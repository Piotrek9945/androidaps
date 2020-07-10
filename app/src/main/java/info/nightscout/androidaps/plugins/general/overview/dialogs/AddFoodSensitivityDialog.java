package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import androidx.fragment.app.DialogFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.food.EcarbBolusService;
import info.nightscout.androidaps.plugins.general.food.Food;

public class AddFoodSensitivityDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(AddFoodSensitivityDialog.class);

    public final static Double SENSITIVITY_BOLUS_FACTOR_GRADE_1 = 1d;
    public final static Double SENSITIVITY_BOLUS_FACTOR_GRADE_2 = 0.9d;
    public final static Double SENSITIVITY_BOLUS_FACTOR_GRADE_3 = 0.8d;
    public final static Double SENSITIVITY_BOLUS_FACTOR_GRADE_4 = 0.7d;

    private List<Food> foodList;
    private boolean isCarbsOnly;

    private RadioGroup sensitivityRadioGroup;

    //one shot guards
    private boolean okClicked;

    public AddFoodSensitivityDialog(List<Food> foodList, boolean isCarbsOnly) {
        this.foodList = foodList;
        this.isCarbsOnly = isCarbsOnly;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_addfood_sensitivity_dialog, container, false);

        view.findViewById(R.id.mdtp_ok).setOnClickListener(this);
        view.findViewById(R.id.mdtp_cancel).setOnClickListener(this);

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
            case R.id.mdtp_ok:
                submit();
                break;
            case R.id.mdtp_cancel:
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
            setSensitivityFactor(getSensitivityFactor(), foodList);
            EcarbBolusService.generateTreatment(getContext(), foodList, isCarbsOnly);
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    public static void setSensitivityFactor(Double sensitivityFactor, List<Food> foodList) {
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
            default: throw new NullPointerException();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }
}
