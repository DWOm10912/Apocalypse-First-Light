from .importer import main
try:
    main()
except (ValueError,OSError,KeyError) as e:
    raise SystemExit(str(e))
