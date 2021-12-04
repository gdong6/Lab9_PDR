package com.example.lab9_pdr;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {

    TextView stepNum;
    TextView distance;
    TextView turnInfo;
    ArrayList RawData = new ArrayList(); //!DO NOT USE DIRECTLY!! will CHANGE if read NEW file
    ArrayList ExpoAvg = new ArrayList(); //!DO NOT USE DIRECTLY!! will CHANGE if read NEW file

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        stepNum = findViewById(R.id.stepNum);
        distance = findViewById(R.id.distance);
        turnInfo = findViewById(R.id.turnInfo);

        RawData = csvReader("WALKING.csv",3); //3 accel_z
        //Log.i("D", RawData.toString());
        ExpoAvg = Ewma(0.02, RawData);
        //Log.i("D", ExpoAvg.toString());

        double avg = getAvg(ExpoAvg);
        //Log.i("Average", ""+avg);

        int STEP = stepCounter(avg, ExpoAvg);
        Log.i("Step", "WALKING.csv contains "+STEP +" Steps");
        setStepNumAndDistance(STEP); //屏幕显示数字


        RawData = csvReader("WALKING_AND_TURNING.csv",3); //3 accel_z
        //Log.i("data", RawData.toString());
        ExpoAvg = Ewma(0.02, RawData);

        double avg2 = getAvg(ExpoAvg);
        //Log.i("Average", ""+avg2);

        int STEP2 = stepCounter(avg2, ExpoAvg);
        Log.i("Step2", "WALKING_AND_TURNING.csv contains "+STEP2 +" Steps");
        setStepNumAndDistance(STEP2); //屏幕显示数字

        RawData = csvReader("WALKING_AND_TURNING.csv",6); //gyro_z
        ArrayList time = csvReader("WALKING_AND_TURNING.csv",0); //timestamp
        ArrayList turn = turnCounter(time, RawData);
        Log.i("turn", "The turning degree is " + turn.toString());
        setTurnInfo(turn);

    }

    /*
     * set phone display. Since each stride is 1 meter, step number equal distance
     */
    public void setStepNumAndDistance(int stepNum) {
        this.stepNum.setText(""+stepNum);
        this.distance.setText(stepNum + " m");
    }

    public void setTurnInfo(ArrayList turn){
        this.turnInfo.setText(turn.toString());
    }
    /*
     * read from a csv file and parse into an arraylist.(The header of the column  has been removed)
     * @String fileName
     * @int column the column need to be parsed
     */
    public ArrayList csvReader(String fileName, int column){
        ArrayList rawData = new ArrayList();
        try {
            InputStream is =  getApplicationContext().getAssets().open(fileName);
            CSVReader reader = new CSVReader(new InputStreamReader(is));
            String [] nextLine;
            reader.readNext(); //跳过表头
            while ((nextLine = reader.readNext()) != null) {
                if(nextLine.length >= column){
                    // nextLine[] is an array of values from the line

                    rawData.add(Double.parseDouble(nextLine[column]));
                }
                else {
                    rawData.add(0.0); //add lost data
                }
            }
            is.close();
        } catch (CsvValidationException | IOException e) {
            e.printStackTrace();
            Log.e("读取错误","文件读取错误");
        }

        return rawData;

    }

    /*
     * given any arraylist of data, it will smooth the data by EWMA.
     */
    public ArrayList Ewma(double alpha, ArrayList arrayList){

        double Alpha = alpha;
        ArrayList originalData = arrayList;
        ArrayList expoData = new ArrayList();

        expoData.add((double)originalData.get(0)); //set first number

        for(int i = 1; i < originalData.size(); i++){
            double curr = alpha * (double)originalData.get(i) + (1-alpha) * (double)expoData.get(i-1);
            expoData.add(curr);
        }

        return expoData;
    }

    /*
     * find the max value of a list of data
     */
    public double getMax(ArrayList arrayList){
        double Max = 0.0;
        for(int i = 0; i < arrayList.size(); i++){
            if ((double)arrayList.get(i) > Max){
                Max = (double) arrayList.get(i);
            }
        }
        return Max;
    }

    /*
     * find the average of a list of data
     */
    public double getAvg(ArrayList arrayList){
        double sum = 0.0;
        for(int i = 0; i < arrayList.size(); i++){
            double cur = (double) arrayList.get(i);
            sum += cur;
        }
        return sum/arrayList.size();
    }

    /*
     * count the step by counting the peak. Offset could be used to increase the accuracy
     */
    public int stepCounter(double avg, ArrayList arrayList){
        int stepNum = 0;

        double offset = (getMax(arrayList) - avg) * 0.3; //eliminate slightly unstable when holding a phone by hand

        for(int i = 1; i < arrayList.size(); i++){
            if((double)arrayList.get(i) >= avg+offset && (double)arrayList.get(i-1) < avg+offset){
                stepNum += 1;
            }
        }

        return stepNum;
    }

    public ArrayList turnCounter(ArrayList time, ArrayList arrayList){
        ArrayList turnlist = new ArrayList(); //a list of real degree of turnings
        ArrayList relativeTurn = new ArrayList(); //turn relative to previous one

        ArrayList cookedData = new ArrayList(); //temporary storage for processed data
        cookedData.add(arrayList.get(0)); //add first number

        for(int i = 1; i < arrayList.size(); i++){
            double cur = (double)cookedData.get(i-1) + (double)arrayList.get(i) * ((double)time.get(i)-(double)time.get(i-1)) / Math.pow(10,9);
            cookedData.add(cur);
        }

        int moveStep = 500;
        ArrayList temp = new ArrayList();
        for(int i = 0; i < cookedData.size() - moveStep; i+=moveStep){
            if(Math.abs((double)cookedData.get(i+500) - (double)cookedData.get(i)) < 0.3){ //0.3 is allowed deviation
                temp.add((double)cookedData.get(i));
            }
        }

        for(int i = 0; i < temp.size()-1; i++){ //remove similar data
            if(Math.abs((double)temp.get(i+1) - (double)temp.get(i)) < 0.3){ //0.3 is allowed deviation
                temp.remove(i);
                i--;
            }
        }

        for(int i = 1; i < temp.size(); i++){ //calculate relative turning value
            relativeTurn.add((double)temp.get(i) - (double)temp.get(i-1));
        }

        for(int i = 0; i < relativeTurn.size(); i++){ //turn value to true degree
            int degree = (int)Math.round((double)relativeTurn.get(i)/0.75) * 45;
            turnlist.add(degree);
        }

        return turnlist;
    }


}