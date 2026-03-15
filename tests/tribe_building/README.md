Run the isolated tribe-building Java suite with:

```bash
mvn -q test
```

from:

```bash
tests/tribe_building
```

This suite is intentionally separate from the main Maven module so card-data checks can
run even when unrelated application code is incomplete or temporarily uncompilable.
