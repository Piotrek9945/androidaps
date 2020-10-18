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

import java.util.Date;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;

public class TbrDialog extends DialogFragment implements OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(TbrDialog.class);

    private final int DURATION_MIN2 = 72;
    public static final int TBR_PERCENTAGE_MIN2 = 80;

    private final int DURATION_MIN1 = 72;
    public static final int TBR_PERCENTAGE_MIN1 = 90;

    private final int DURATION_0 = 12;
    public static final int TEMP_TARGET_0 = 90;
    public static final int TBR_PERCENTAGE_0 = 110;

    private final int DURATION_1 = 12;
    public static final int TEMP_TARGET_1 = 90;
    public static final int TBR_PERCENTAGE_1 = 100;

    private final int DURATION_2 = 12;
    public static final int TEMP_TARGET_2 = 100;
    public static final int TBR_PERCENTAGE_2 = 70;


    private final int DURATION_3 = 12;
    public static final int TEMP_TARGET_3 = 110;
    public static final int TBR_PERCENTAGE_3 = 50;

    private final int DURATION_4 = 12;
    public static final int TEMP_TARGET_4 = 130;
    public static final int TBR_PERCENTAGE_4 = 20;

    private RadioGroup tbrRadioGroup;
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

        view.findViewById(R.id.mdtp_ok).setOnClickListener(this);
        view.findViewById(R.id.mdtp_cancel).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        tbrRadioGroup = view.findViewById(R.id.tbr_grade);

        resetButton = view.findViewById(R.id.tbr_reset_button);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setTBR(100, 0);
                setTempTarget(120, 0);
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
            int tbrPercentage = getTbrPercentage();
            int durationInHours;
            switch(tbrPercentage) {
                case -2:
                    durationInHours = DURATION_MIN2;
                    setTBR(TBR_PERCENTAGE_MIN2, durationInHours);
                    break;

                case -1:
                    durationInHours = DURATION_MIN1;
                    setTBR(TBR_PERCENTAGE_MIN1, durationInHours);
                    break;

                case 0:
                    durationInHours = DURATION_0;
//                    setTempTarget(TEMP_TARGET_0, durationInHours);
                    setTBR(TBR_PERCENTAGE_0, durationInHours);
                    break;

                case 1:
                    durationInHours = DURATION_1;
//                    setTempTarget(TEMP_TARGET_1, durationInHours);
                    setTBR(TBR_PERCENTAGE_1, durationInHours);
                    break;

                case 2:
                    durationInHours = DURATION_2;
//                    setTempTarget(TEMP_TARGET_2, durationInHours);
                    setTBR(TBR_PERCENTAGE_2, durationInHours);
                    break;

                case 3:
                    durationInHours = DURATION_3;
//                    setTempTarget(TEMP_TARGET_3, durationInHours);
                    setTBR(TBR_PERCENTAGE_3, durationInHours);
                    break;

                case 4:
                    durationInHours = DURATION_4;
//                    setTempTarget(TEMP_TARGET_4, durationInHours);
                    setTBR(TBR_PERCENTAGE_4, durationInHours);
                    break;

                default: throw new NullPointerException();
            }
            dismiss();
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
    }

    private void setTBR(int tbrPercentage, int durationInHours) {
        String currentProfileName = TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(DateUtil.now()).profileName;
        int durationInMinutes = durationInHours * 60;
        ProfileFunctions.doProfileSwitch(ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getProfile(), currentProfileName, durationInMinutes, tbrPercentage, 0, DateUtil.now());
    }

    private int getTbrPercentage() {
        switch (tbrRadioGroup.getCheckedRadioButtonId()) {
            case R.id.tbr_percentage_min2: return -2;
            case R.id.tbr_percentage_min1: return -1;
            case R.id.tbr_percentage_0: return 0;
            case R.id.tbr_percentage_1: return 1;
            case R.id.tbr_percentage_2: return 2;
            case R.id.tbr_percentage_3: return 3;
            case R.id.tbr_percentage_4: return 4;
            default: throw new NullPointerException();
        }
    }

    private void setTempTarget(double target, int durationInHours) {
        int durationInMinutes = durationInHours * 60;
        final String reason = "Ręczne";
        double targetBottom = target;
        double targetTop = target;
        if ((targetBottom != 0d && targetTop != 0d) || durationInMinutes == 0) {
            TempTarget tempTarget = new TempTarget()
                    .date(new Date().getTime())
                    .duration(durationInMinutes)
                    .reason(reason)
                    .source(Source.USER);
            if (tempTarget.durationInMinutes != 0) {
                tempTarget.low(Profile.toMgdl(targetBottom, Constants.MGDL))
                        .high(Profile.toMgdl(targetTop, Constants.MGDL));
            } else {
                tempTarget.low(0).high(0);
            }
            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {

    }
}
