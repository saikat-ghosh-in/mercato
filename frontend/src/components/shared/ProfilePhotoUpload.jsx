import { useEffect, useRef, useState } from 'react';
import { FiUpload } from 'react-icons/fi';
import api,{session} from '../../backend/api';
import UserAvatar from './UserAvatar';
import { profileImageSource } from '../../utils/profileImage';

export default function ProfilePhotoUpload({imageUrl,name,onChange,onBusyChange}){
  const input=useRef(null),alive=useRef(true);const [busy,setBusy]=useState(false),[error,setError]=useState('');
  useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
  useEffect(()=>{onBusyChange?.(busy);},[busy,onBusyChange]);
  const upload=async event=>{
    const file=event.target.files?.[0];event.target.value='';if(!file||busy)return;setError('');
    if(!['image/jpeg','image/png','image/webp'].includes(file.type)){setError('Choose a JPG, PNG or WebP image.');return;}
    if(file.size>5*1024*1024){setError('Choose an image smaller than 5 MB.');return;}
    const epoch=session.getSnapshot().epoch;const current=()=>alive.current&&epoch===session.getSnapshot().epoch;setBusy(true);
    try{const body=new FormData();body.append('file',file);const {data}=await api.post('/user/profile-image',body);if(!current())return;
      if(!profileImageSource(data?.imageUrl))throw Error('The uploaded image could not be confirmed.');onChange(data.imageUrl);
    }catch(failure){if(current())setError(failure.response?.data?.message||'Your photo could not be uploaded. Please try again.');}
    finally{if(current())setBusy(false);}
  };
  return <section className="surface profile-photo-section" aria-label="Profile photo"><UserAvatar className="profile-photo-preview" imageUrl={imageUrl} name={name} label="Profile photo preview"/><div className="min-w-0"><h2>Profile photo</h2><p>JPG, PNG or WebP · up to 5 MB</p><input ref={input} aria-label="Upload profile photo" type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" disabled={busy} onChange={upload}/><div className="profile-photo-actions"><button type="button" className="button button-outline" disabled={busy} onClick={()=>input.current?.click()}><FiUpload aria-hidden="true"/>{busy?'Uploading…':profileImageSource(imageUrl)?'Change photo':'Upload photo'}</button>{profileImageSource(imageUrl)&&<button type="button" className="text-link" disabled={busy} onClick={()=>{setError('');onChange('');}}>Remove photo</button>}</div><p className="profile-photo-note">Choose Save changes to keep your photo.</p>{error&&<p role="alert" className="inline-error">{error}</p>}</div></section>;
}
