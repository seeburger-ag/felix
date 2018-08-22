# Why you should use the system ready project

## Error reporting and root cause analysis

When a system fails to become ready after a certain amount of time, the follow up question is : "What is wrong?". So the framework also provides a way for each system check to report back information about errors.

One typical error is that a DS component does not come up because some mandatory dependency is not present. This dependency can be a configuration or maybe a service reference. In many cases, a trivial error like a missing configuration for a low level component can cause a lot of other components to fail to register. The root cause analysis allows traversing the trees of components and to narrow the reason for the system not being ready to a minimal number of possible causes for the failure state.

## Use cases

Typical use cases for the system ready check ordered by the phase of the development cycle.

### Development
* Coarse check of a pax exam based setup before letting the actual junit tests work on the system
* Determining the root cause of a pax exam based setup not starting

### QA
* Starting system tests as soon as a system is ready for the tests to proceed
* Determining the root cause for an OSGi based system not starting up

### Deployment
* Signaling when a system is ready after an upgrade so that it can receive traffic in the case of blue / green deployments

### Production
* Periodic _Ready health checks_ for system and signaling this state to monitoring systems
* Container automation and driving _autopilot container_ deployments.

## Added value when using the system ready framework

* Increased deployment resilience as deployments are checked before being exposed
* Faster deployments / upgrades by replacing fixed, error-prone waits with ready based waits
* Lower bug tracking efforts in tests as root causes are easier to find
* Configurability of _when_ a certain deployment is considered ready (e.g. after content sync with an external service)
