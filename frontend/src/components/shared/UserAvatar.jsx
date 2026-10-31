import { useState } from 'react';
import { profileImageSource } from '../../utils/profileImage';
export default function UserAvatar({name='Your account',imageUrl,className='',label='Profile photo'}){
  const [failed,setFailed]=useState(null);const image=profileImageSource(imageUrl);
  return <span className={`user-avatar ${className}`} role="img" aria-label={label}>{image&&image!==failed?<img src={image} alt="" onError={()=>setFailed(image)} referrerPolicy="no-referrer"/>:<span aria-hidden="true">{Array.from(name.trim()||'U')[0].toLocaleUpperCase()}</span>}</span>;
}
