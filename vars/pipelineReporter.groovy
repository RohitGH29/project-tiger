import groovy.json.JsonOutput
import hudson.model.Cause
import org.jenkinsci.plugins.workflow.actions.LabelAction
import org.jenkinsci.plugins.workflow.actions.TimingAction
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker
import org.jenkinsci.plugins.workflow.graph.FlowNode

def call() {
  try {
    // def detectorUrl = env.PIPELINE_DETECTOR_URL ?: 'http://project-alb-986991570.ap-south-1.elb.amazonaws.com:8000/'
    def detectorUrl = env.PIPELINE_DETECTOR_URL ?: 'http://localhost:8000/'
    def payload = buildPayload(
      currentBuild.rawBuild,
      env.JOB_NAME,
      env.BUILD_NUMBER,
      currentBuild.currentResult ?: 'UNKNOWN',
      env.BRANCH_NAME ?: env.GIT_BRANCH ?: 'main',
      env.GIT_COMMIT
    )
    postPayload(detectorUrl, payload)
  } catch (Throwable ignored) {
    echo "pipelineReporter skipped: ${ignored.getClass().getSimpleName()}: ${ignored.message}"
  }
}

@NonCPS
private Map buildPayload(build, String jobName, Object buildNumber, String result, String branchName, String gitCommit) {
  def buildStart = build.getStartTimeInMillis() ?: System.currentTimeMillis()
  def reportedDuration = build.duration ?: 0L
  def fallbackBuildEnd = reportedDuration > 0L ? (buildStart + reportedDuration) : Math.max(buildStart, System.currentTimeMillis())
  def rawStages = collectStages(build, buildStart, fallbackBuildEnd)
  def duration = reportedDuration > 0L ? reportedDuration : deriveTotalDuration(buildStart, fallbackBuildEnd, rawStages)
  def stages = rawStages.collect { stage ->
    [
      stage_name : stage.stage_name,
      status     : stage.status,
      duration_ms: stage.duration_ms,
      started_at : toIso(stage.start_millis),
      finished_at: toIso(stage.finish_millis),
      log_text   : stage.log_text,
      node_label : stage.node_label,
    ]
  }

  return [
    job_name         : jobName,
    build_number     : buildNumber as Integer,
    status           : result,
    branch           : branchName ?: 'main',
    commit_sha       : gitCommit?.take(40),
    triggered_by     : resolveTriggeredBy(build),
    total_duration_ms: duration,
    started_at       : toIso(buildStart),
    finished_at      : toIso(buildStart + duration),
    stages           : stages,
  ]
}

private void postPayload(String detectorUrl, Map payload) {
  def body = JsonOutput.toJson(payload)
  writeFile file: '.pipeline-detector-payload.json', text: body
  try {
    sh """
      curl --silent --show-error --fail \
        --max-time 20 \
        --header 'Content-Type: application/json' \
        --data @.pipeline-detector-payload.json \
        '${detectorUrl}/api/builds'
    """
  } finally {
    sh "rm -f .pipeline-detector-payload.json"
  }
}

@NonCPS
private List<Map> collectStages(build, long buildStart, long fallbackBuildEnd) {
  def allLogs = build.getLog(500).join('\n')
  def walker = new FlowGraphWalker(build.execution)
  def rawStages = []

  for (FlowNode node in walker) {
    def label = node.getAction(LabelAction)
    if (label == null) {
      continue
    }
    def stageName = label.displayName
    if (!stageName || rawStages.find { it.stage_name == stageName }) {
      continue
    }

    def startMillis = resolveNodeStartMillis(node, buildStart)
    rawStages << [
      stage_name : stageName,
      status     : inferStageStatus(stageName, build),
      start_millis: startMillis,
      log_text   : allLogs.take(10000),
      node_label : node.displayName,
    ] 
  }

  rawStages = rawStages.sort { a, b -> a.start_millis <=> b.start_millis }
  def stages = []

  for (int index = 0; index < rawStages.size(); index++) {
    def current = rawStages[index]
    def nextStart = index + 1 < rawStages.size() ? rawStages[index + 1].start_millis : fallbackBuildEnd
    def startMillis = current.start_millis ?: buildStart
    def finishMillis = Math.max(startMillis, nextStart ?: fallbackBuildEnd)
    def duration = Math.max(0L, finishMillis - startMillis)

    stages << [
      stage_name   : current.stage_name,
      status       : current.status,
      duration_ms  : duration,
      start_millis : startMillis,
      finish_millis: finishMillis,
      log_text     : current.log_text,
      node_label   : current.node_label,
    ]
  }

  return stages
}

@NonCPS
private long deriveTotalDuration(long buildStart, long fallbackBuildEnd, List<Map> stages) {
  if (!stages) {
    return Math.max(0L, fallbackBuildEnd - buildStart)
  }

  def latestStageEnd = stages.collect { it.finish_millis as long }.max() ?: buildStart
  def summedStageDurations = stages.collect { it.duration_ms as long }.sum(0L)
  return Math.max(
    Math.max(0L, latestStageEnd - buildStart),
    Math.max(Math.max(0L, fallbackBuildEnd - buildStart), summedStageDurations)
  )
}

@NonCPS
private long resolveNodeStartMillis(FlowNode node, long fallback) {
  try {
    def timing = TimingAction.getStartTime(node)
    return timing ?: fallback
  } catch (Exception ignored) {
    return fallback
  }
}

@NonCPS
private String inferStageStatus(String stageName, build) {
  if ((build.result ?: 'SUCCESS') == 'FAILURE') {
    return 'FAILURE'
  }
  return 'SUCCESS'
}

@NonCPS
private String resolveTriggeredBy(build) {
  def causes = build.getCauses()
  for (def cause in causes) {
    if (cause instanceof Cause.UserIdCause) {
      return cause.getUserId() ?: cause.getUserName()
    }
  }
  return causes ? causes[0].shortDescription : 'unknown'
}

@NonCPS
private String toIso(long millis) {
  return new Date(millis).format("yyyy-MM-dd'T'HH:mm:ssXXX", TimeZone.getTimeZone('UTC'))
}
