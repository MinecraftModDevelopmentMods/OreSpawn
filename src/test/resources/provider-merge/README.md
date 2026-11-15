# Retained Provider Merge Fixtures

These are structured JSON extracts, not generated worlds. They retain the ten
affected Base Metals rules and their original placement values.

- `retained-basemetals-ores.json`: selected rules and the Base Metals manifest
  from the released base* 1.10 fixture retained by the 1.12 upgrade tests.
  Complete source-profile SHA-256:
  `AF4A6124287D5B40F81BCC05A3B013B5C94F903A221F3C7B48FB6B0842E72197`.
- `current-basemetals-ores.json`: the corresponding rules from Base Metals'
  26.1.2 provider declaration. Complete source-declaration SHA-256:
  `3963718AA0464BDB240DA5521C65EA1479AC46D34B0FF8EAD2675013C93B05D0`.

The regression tests call the real provider merge, temporarily installing
only this provider and restoring its prior state afterwards. No Base Metals
classes, jars, or live world folders are needed to run them.
