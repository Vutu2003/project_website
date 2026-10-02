#!/usr/bin/env python3
"""Bind BCrypt hashes from the ignored security env; never emit credential values."""
import argparse, os, subprocess
import bcrypt

ACCOUNTS = [('admin','LOCAL_ADMIN_PASSWORD'),('vtyt','LOCAL_VTYT_PASSWORD'),
            ('bgd','LOCAL_BGD_PASSWORD'),('khoa_noi','LOCAL_KHOA_NOI_PASSWORD'),
            ('khoa_ngoai','LOCAL_KHOA_NGOAI_PASSWORD')]

def connection():
    return ['psql','-X','-q','-v','ON_ERROR_STOP=1','-h',os.environ['DB_HOST'],
            '-p',os.environ['DB_PORT'],'-U',os.environ['DB_USERNAME'],'-d',os.environ['DB_NAME']]

def hashes():
    return {user:bcrypt.hashpw(os.environ[key].encode(),bcrypt.gensalt(rounds=12)).decode()
            for user,key in ACCOUNTS}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--update',action='store_true');args=parser.parse_args()
    assert os.environ['DB_HOST']=='127.0.0.1'
    database=os.environ['DB_NAME']
    assert database=='medical_maintenance_v2' or (database.startswith('medical_maintenance_') and database.endswith('_test'))
    env=dict(os.environ,PGPASSWORD=os.environ['DB_PASSWORD'])
    values=hashes()
    if args.update:
        sql='BEGIN;\n'
        for user,digest in values.items():
            sql+=f"UPDATE user_account SET password_hash='{digest}' WHERE username='{user}';\n"
        sql+='COMMIT;'
        subprocess.run(connection(),input=sql,text=True,check=True,env=env,stdout=subprocess.DEVNULL)
    else:
        # Only hashes travel via stdin into psql variables, never command arguments.
        sql='\n'.join(f"\\set hash_{user} '{digest}'" for user,digest in values.items())
        from pathlib import Path
        sql+='\nBEGIN;\n'+(Path(__file__).parent/'v2_canonical.sql').read_text()+'\nCOMMIT;'
        subprocess.run(connection(),input=sql,text=True,check=True,env=env,stdout=subprocess.DEVNULL)
    print('Canonical local accounts configured with BCrypt hashes.')
