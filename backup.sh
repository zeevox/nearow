filename=report-$(date +%s%3N).zip
zip -r9 $filename report/
scp $filename terminator:/mnt/freezer/users/zeevox/zeevox/
