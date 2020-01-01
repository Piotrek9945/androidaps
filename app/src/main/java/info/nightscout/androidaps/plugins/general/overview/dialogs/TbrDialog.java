package info.nightscout.androidaps.plugins.general.overview.dialogs;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import androidx.fragment.app.DialogFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.food.FoodUtils;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.utils.NumberPicker;

public class TbrDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(TbrDialog.class);

    private final int TBR_PERCENTAGE_RESET = 100;
    private final int TBR_PERCENTAGE_1 = 85;
    private final int TBR_PERCENTAGE_2 = 75;
    private final int TBR_PERCENTAGE_3 = 60;
    private final int TBR_PERCENTAGE_4 = 50;

    private RadioGroup tbrRadioGroup;
    private NumberPicker durationPicker;
    private Button resetButton;

    //one shot guards
    private boolean okClicked;

    public TbrDialog() {}

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {}

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_tbr_dialog, container, false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        tbrRadioGroup = view.findViewById(R.id.tbr_grade);

        durationPicker = view.findViewById(R.id.tbr_duration);
        durationPicker.setParams(1d, 1d, 48d, 1d, new DecimalFormat("0"), false, view.findViewById(R.id.ok), textWatcher);
        durationPicker.setOnClickListener(this);

        resetButton = view.findViewById(R.id.tbr_reset_button);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setTbr(100, 0);
                dismiss();
            }
        });

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
            int tbrPercentage = getTbrPercentage();
            int durationInHours = FoodUtils.Companion.roundDoubleToInt(durationPicker.getValue());
            setTbr(tbrPercentage, durationInHours);
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    private void setTbr(int tbrPercentage, int durationInHours) {
        if (durationInHours < 0) dismiss();
        int durationInMinutes = durationInHours * 60;
        ProfileFunctions.doProfileSwitch(ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getProfile(), "LocalProfile", durationInMinutes, tbrPercentage, 0);
    }

    private int getTbrPercentage() {
        switch (tbrRadioGroup.getCheckedRadioButtonId()) {
            case R.id.tbr_percentage_1: return TBR_PERCENTAGE_1;
            case R.id.tbr_percentage_2: return TBR_PERCENTAGE_2;
            case R.id.tbr_percentage_3: return TBR_PERCENTAGE_3;
            case R.id.tbr_percentage_4: return TBR_PERCENTAGE_4;
            default: throw new NullPointerException();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }
}
