# Rules are added only where a library needs them. None are needed today:
# - kotlinx.serialization (API models, navigation routes) ships its own
#   consumer rules for @Serializable classes;
# - AndroidSVG doesn't use reflection;
# - Room, WorkManager, Media3 and Coil ship consumer rules.
# The release build runs on a phone end to end (Task 34), which is what
# would catch a missing rule.
