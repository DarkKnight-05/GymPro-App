# GymPro

GymPro is a single-gym Android management app for BodyTech.

## Included
- Admin and Trainer accounts
- Branch-based access control
- Member management and profile photos
- Membership plans and expiry tracking
- Cash/GPay payments and payment history
- Body measurements and history
- Archive/restore members
- Branch-wise expiring membership view
- Admin reports
- Manual member Excel export
- Fee-due notifications
- Firebase Firestore, Authentication and Storage sync

## Not included
- Multi-gym selection or Gym Code login
- Attendance management
- Expenses tracking
- Progress Photos
- Member login accounts
- Trainer email accounts

The internal Firebase `gymId` remains in the data model only as a tenant/security boundary for the single gym; it is not exposed as a user-facing feature.
