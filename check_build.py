#!/usr/bin/env python3
import subprocess, json, time, sys, os

# Read token
cred_path = os.path.expanduser("~/.git-credentials")
with open(cred_path) as f:
    line = f.read().strip()
token = line.split(":")[-1].split("@")[0]

headers = [
    f"Authorization: token {token}",
    "Accept: application/vnd.github.v3+json"
]

for i in range(15):
    time.sleep(30)
    cmd = ["curl", "-s"]
    for h in headers:
        cmd += ["-H", h]
    cmd += [f"https://api.github.com/repos/ruit478/setmeal-android/actions/runs"]
    
    result = subprocess.run(cmd, capture_output=True, text=True)
    data = json.loads(result.stdout)
    runs = data.get("workflow_runs", [])
    
    if runs:
        r = runs[0]
        status = r["status"]
        conclusion = r.get("conclusion", "")
        run_id = r["id"]
        branch = r["head_branch"]
        print(f"[{i+1}] {status} | {conclusion} | run={run_id} | branch={branch}")
        
        if status == "completed":
            print(f"=== BUILD {conclusion} ===")
            print(f"RUN_ID={run_id}")
            
            # Get job details
            job_cmd = ["curl", "-s"]
            for h in headers:
                job_cmd += ["-H", h]
            job_cmd += [f"https://api.github.com/repos/ruit478/setmeal-android/actions/runs/{run_id}/jobs"]
            job_result = subprocess.run(job_cmd, capture_output=True, text=True)
            job_data = json.loads(job_result.stdout)
            for job in job_data.get("jobs", []):
                print(f"  Job: {job['name']} - {job.get('conclusion', 'n/a')}")
                for step in job.get("steps", []):
                    if step.get("conclusion") == "failure":
                        print(f"    FAILED: {step['name']}")
                        # Get step logs
                        log_cmd = ["curl", "-s"]
                        for h in headers:
                            log_cmd += ["-H", h]
                        log_cmd += [f"https://api.github.com/repos/ruit478/setmeal-android/actions/jobs/{job['id']}/logs"]
                        # Just store the URL for now
                        print(f"    Logs: {step.get('html_url', '')}")
            break
    else:
        print(f"[{i+1}] no runs yet")

print("DONE")
