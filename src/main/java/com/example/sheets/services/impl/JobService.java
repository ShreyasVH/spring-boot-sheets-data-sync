package com.example.sheets.services.impl;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.spring.annotations.Recurring;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JobService {

    @Autowired
    private Sheets sheetsService;

    @Recurring(id = "my-recurring-job", cron = "0/3 * * * *")
    @Job(name = "Daily job")
    public void dailyJob(JobContext jobContext) throws IOException {
        jobContext.logger().info("started job");

        String sheetId = "1im3kg31RL7FDNL_V_R7tSwqNq66vgKgpvzrn0ADGzZs";

        ValueRange response = sheetsService.spreadsheets().values()
                .get(sheetId, "Hospital!A2:E9")
                .execute();

//        jobContext.logger().info(response.getValues());
        String sh = "sh";
    }
}