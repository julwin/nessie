# Nessie Specification

Nessie Specifications define the behaviour of Nessie Servers. The same behaviour can be expected to be observable
via all supported APIs (unless noted otherwise).

[Semver](https://semver.org/spec/v2.0.0.html) principles are followed in bumping the Nessie spec version.
For example, a major behaviour change will result in incrementing the major version number.

The patch version number will be incremented only on minute spec corrections that do not significantly alter the 
server's behaviour.

If a server returns a specific spec version via `NessieConfiguration.getSpecVersion()` it MUST comply with the
specified behaviour (see sections below).

Servers SHOULD return the spec version that defines their behaviour most completely.

A server SHOULD return `null` from `NessieConfiguration.getSpecVersion()`, when it behaves in a way that is not
compatible with any known spec version.

Servers MAY show behaviours that are not covered by the spec, as long as those behaviours do not contradict the
well-specified behaviours.

Refer to the [Nessie API documentation](./README.md) for the meaning of Nessie-specific terms.

# 2.0.0-beta.1

* Namespaces are expected to be created before they are referenced. Namespaces may be referenced within the same 
  commit that creates them. 
* If a non-existent Namespace is referenced in a commit, the server should fail the corresponding change (commit 
  / merge / transplant) with the `NAMESPACE_ABSENT` conflict type.
* `Unchanged` Operations may be submitted in commits, but the server does not persist them. Consequently, `Unchanged`
  operations are not considered during merges and transplants, even when they were part of the original commit.
* The server MUST consider `Unchanged` operations during the handling of plain commits and MUST raise commit conflicts
  if those operations clash with operations in the commits log since the `expected hash` provided by the client.

## Related Server Configuration

Note that setting the `nessie.version.store.advanced.validate-namespaces` or
`nessie.version.store.persist.namespace-validation` configuration properties to `false` will 
make the server violate the Namespace existence validation rule (above). Nessie Server administrators MUST NOT set those
properties to `false` on pre-built servers published by Project Nessie. If the server is custom-built and namespace
validation is disabled, `NessieConfiguration.getSpecVersion()` MUST NOT return `2.0.0-beta.1`.

Similarly, if a Nessie repository is imported, the administrator is responsible for ensuring that the imported data
has Namespace objects defined appropriately throughout the commit history (all HEADs) in order to claim support for
spec version `2.0.0-beta.1`.
