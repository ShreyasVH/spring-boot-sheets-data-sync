package com.example.sheets.services.impl;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.spring.annotations.Recurring;
import org.springframework.stereotype.Component;

@Component
public class JobService {

    @Recurring(id = "my-recurring-job", cron = "0/3 * * * *")
    @Job(name = "Daily job")
    public void dailyJob(JobContext jobContext) {
        jobContext.logger().info("started job");
    }
}