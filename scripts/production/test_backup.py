"""Transport fixtures only: no R2 endpoint is contacted."""
import contextlib,importlib.util,io,json,pathlib,tempfile,types,unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('backup',pathlib.Path(__file__).with_name('backup.py'));b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
class BackupTests(unittest.TestCase):
 def test_freshness_fails_closed_for_stale_or_future_records(self):
  import sys,subprocess,datetime
  with tempfile.TemporaryDirectory() as d:
   for hours,expected in [(0,0),(27,1),(-1,1)]:
    now=datetime.datetime.now(datetime.timezone.utc)-datetime.timedelta(hours=hours)
    (pathlib.Path(d)/'last-success.json').write_text(json.dumps({'confirmedAt':now.isoformat()}))
    r=subprocess.run([sys.executable,str(pathlib.Path(__file__).with_name('backup.py')),'status','--out-dir',d],capture_output=True)
    self.assertEqual(r.returncode,expected)

 def test_capture_keeps_stderr_suppressed(self):
  import sys
  result=b.run([sys.executable,"-c","import sys;print(17);print(\"private\",file=sys.stderr)"],capture_output=True)
  self.assertEqual(result.stdout,b"17\n");self.assertIsNone(result.stderr)
 def test_upload_integrity_and_failure(self):
  for corrupt in [False,True]:
   with self.subTest(corrupt=corrupt),tempfile.TemporaryDirectory() as d:
    out=pathlib.Path(d).resolve();artifact=out/'belsen-20261121T020000Z-abcdef123456.dump.age';artifact.write_bytes(b'encrypted-fixture');digest=b.sha256(artifact)
    artifact.with_suffix('.age.json').write_text(json.dumps({'sha256':digest,'bytes':artifact.stat().st_size}))
    args=types.SimpleNamespace(remote_budget_mib=8192,bucket='fixture-bucket')
    def transport(cmd,**kw):
     action=cmd[0];v={}
     if action=='list-objects-v2':v={'Contents':[]}
     if action=='head-object':v={'ContentLength':len(b'encrypted-fixture'),'Metadata':{'sha256':digest}}
     if action=='get-object':pathlib.Path(cmd[-1]).write_bytes(b'corrupt' if corrupt else b'encrypted-fixture')
     return types.SimpleNamespace(stdout=json.dumps(v).encode())
    with patch.object(b,'aws',return_value=([],{})),patch.object(b,'run',side_effect=transport),contextlib.redirect_stdout(io.StringIO()):
     if corrupt:
      with self.assertRaises(ValueError):b.upload(args,out,artifact)
      self.assertFalse((out/'last-success.json').exists())
     else:
      b.upload(args,out,artifact);self.assertTrue((out/'last-success.json').exists())
 def test_bad_local_digest_fails_before_transport(self):
  with tempfile.TemporaryDirectory() as d:
   out=pathlib.Path(d).resolve();f=out/'belsen-20261121T020000Z-abcdef123456.dump.age';f.write_bytes(b'bad');f.with_suffix('.age.json').write_text('{"sha256":"wrong","bytes":3}')
   with patch.object(b,'aws') as transport:
    with self.assertRaises(ValueError):b.upload(types.SimpleNamespace(),out,f)
    transport.assert_not_called()
 def test_credentials_reject_public_permissions(self):
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)/'credentials';p.write_text('fixture');p.chmod(0o644)
   with self.assertRaises(ValueError):b.private_file(p)
if __name__=='__main__':unittest.main()
