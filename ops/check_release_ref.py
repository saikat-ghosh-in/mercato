#!/usr/bin/env python3
"""Stable releases come from main; explicitly labeled prereleases may come from reviewed stg."""
import os
import re
import subprocess

def required_base(tag, prerelease):
    if not re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?', tag):
        raise ValueError('Use an immutable version tag')
    has_suffix='-' in tag
    if prerelease != has_suffix:
        raise ValueError('Prerelease tags and GitHub prerelease status must agree')
    return 'origin/stg' if prerelease else 'origin/main'

if __name__=='__main__':
    base=required_base(os.environ['RELEASE_TAG'],os.environ.get('RELEASE_IS_PRERELEASE')=='true')
    subprocess.run(['git','merge-base','--is-ancestor','HEAD',base],check=True)
    print('Release ancestry verified against '+base)
